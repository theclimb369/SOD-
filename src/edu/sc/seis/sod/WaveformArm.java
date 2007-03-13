package edu.sc.seis.sod;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import edu.iris.Fissures.IfNetwork.Channel;
import edu.iris.Fissures.IfNetwork.NetworkNotFound;
import edu.iris.Fissures.IfNetwork.Station;
import edu.iris.Fissures.model.MicroSecondDate;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.network.ChannelIdUtil;
import edu.iris.Fissures.network.NetworkIdUtil;
import edu.iris.Fissures.network.SiteIdUtil;
import edu.iris.Fissures.network.StationIdUtil;
import edu.sc.seis.fissuresUtil.cache.WorkerThreadPool;
import edu.sc.seis.fissuresUtil.chooser.ClockUtil;
import edu.sc.seis.fissuresUtil.database.NotFound;
import edu.sc.seis.fissuresUtil.exceptionHandler.GlobalExceptionHandler;
import edu.sc.seis.sod.database.ChannelDbObject;
import edu.sc.seis.sod.database.EventDbObject;
import edu.sc.seis.sod.database.JDBCRetryQueue;
import edu.sc.seis.sod.database.NetworkDbObject;
import edu.sc.seis.sod.database.SiteDbObject;
import edu.sc.seis.sod.database.StationDbObject;
import edu.sc.seis.sod.database.event.JDBCEventStatus;
import edu.sc.seis.sod.database.waveform.JDBCEventChannelStatus;
import edu.sc.seis.sod.process.waveform.WaveformProcess;
import edu.sc.seis.sod.process.waveform.vector.ANDWaveformProcessWrapper;
import edu.sc.seis.sod.status.OutputScheduler;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeLeaf;
import edu.sc.seis.sod.status.waveformArm.WaveformMonitor;
import edu.sc.seis.sod.subsetter.EventEffectiveTimeOverlap;
import edu.sc.seis.sod.subsetter.eventStation.EventStationSubsetter;
import edu.sc.seis.sod.subsetter.eventStation.PassEventStation;

public class WaveformArm implements Arm {

    public WaveformArm(Element config,
                       EventArm eventArm,
                       NetworkArm networkArm,
                       int threadPoolSize) throws Exception {
        RunProperties runProps = Start.getRunProps();
        SERVER_RETRY_DELAY = runProps.getServerRetryDelay();
        eventStatus = new JDBCEventStatus();
        evChanStatus = new JDBCEventChannelStatus();
        retries = new JDBCRetryQueue("waveform");
        retries.setMaxRetries(5);
        int minRetriesOnAvailableData = 3;
        retries.setMinRetries(minRetriesOnAvailableData);
        retries.setMinRetryWait((TimeInterval)runProps.getMaxRetryDelay()
                .divideBy(minRetriesOnAvailableData));
        retries.setEventDataLag(runProps.getSeismogramLatency());
        corbaFailures = new JDBCRetryQueue("corbaFailure");
        corbaFailures.setMinRetryWait(new TimeInterval(2, UnitImpl.HOUR));
        corbaFailures.setMaxRetries(10);
        processConfig(config);
        this.networkArm = networkArm;
        this.eventArm = eventArm;
        pool = new WorkerThreadPool("Waveform EventChannel Processor",
                                    threadPoolSize);
    }

    public boolean isActive() {
        return !finished;
    }

    public String getName() {
        return "WaveformArm";
    }

    public void run() {
        try {
            addSuspendedPairsToQueue(Start.suspendedPairs);
            while(pool.isEmployed()) {
                try {
                    logger.debug("pool employed, sleeping for 100 sec");
                    Thread.sleep(100000);
                } catch(InterruptedException e) {}
            }
            waitForInitialEvent();
            int sleepTime = 5;
            TimeInterval logInterval = new TimeInterval(10, UnitImpl.MINUTE);
            logger.debug("will populateEventChannelDb, then sleep "
                    + sleepTime
                    + " sec between each try to process successful events, log interval is "
                    + logInterval);
            lastEventStartLogTime = ClockUtil.now();
            do {
                int numEvents = populateEventChannelDb();
                retryIfNeededAndAvailable();
                sleepALittle(numEvents, sleepTime, logInterval);
            } while(possibleToContinue());
            logger.info("Main waveform arm done.  Retrying failures.");
            MicroSecondDate runFinishTime = ClockUtil.now();
            MicroSecondDate serverFailDelayEnd = runFinishTime.add(SERVER_RETRY_DELAY);
            corbaFailures.setLastRetryTime(serverFailDelayEnd);
            while(!Start.isArmFailure() && retries.willHaveNext()
                    || corbaFailures.willHaveNext() || pool.isEmployed()) {
                retryIfNeededAndAvailable();
                try {
                    logger.debug("Sleeping while waiting for retries");
                    Thread.sleep(10000);
                } catch(InterruptedException e) {}
            }
            logger.info("Lo!  I am weary of my wisdom, like the bee that hath gathered too much\n"
                    + "honey; I need hands outstretched to take it.");
        } catch(Throwable e) {
            Start.armFailure(this, e);
        }
        finished = true;
        synchronized(OutputScheduler.getDefault()) {
            OutputScheduler.getDefault().notify();
        }
    }

    private boolean possibleToContinue() {
        return !Start.isArmFailure() && eventArm.isActive();
    }

    private void sleepALittle(int numEvents,
                              int sleepTime,
                              TimeInterval logInterval) {
        if(numEvents != 0) {
            // found events so reset logInterval
            lastEventStartLogTime = ClockUtil.now();
        } else if(ClockUtil.now()
                .subtract(lastEventStartLogTime)
                .greaterThan(logInterval)) {
            logger.debug("no successful events found in last " + logInterval);
            lastEventStartLogTime = ClockUtil.now();
        }
        try {
            Thread.sleep(sleepTime * 1000);
        } catch(InterruptedException e) {}
    }

    public LocalSeismogramArm getLocalSeismogramArm() {
        return localSeismogramArm;
    }

    public MotionVectorArm getMotionVectorArm() {
        return motionVectorArm;
    }

    public EventStationSubsetter getEventStationSubsetter() {
        return eventStationSubsetter;
    }

    public WaveformMonitor[] getWaveformArmMonitors() {
        return (WaveformMonitor[])statusMonitors.toArray(new WaveformMonitor[0]);
    }

    // fills the eventchannel db with all available events and starts
    // WaveformWorkerUnits on all inserted event channel pairs
    // If there are no waiting events, this just returns
    private int populateEventChannelDb() throws Exception {
        int numEvents = 0;
        for(EventDbObject ev = popAndGet(); ev != null; ev = popAndGet()) {
            numEvents++;
            EventEffectiveTimeOverlap overlap = new EventEffectiveTimeOverlap(ev.getEvent());
            NetworkDbObject[] networks = networkArm.getSuccessfulNetworks();
            for(int i = 0; i < networks.length; i++) {
                startNetwork(ev, overlap, networks[i]);
            }
            // set the status of the event to be SUCCESS implying that
            // that all the network information for this particular event is
            // inserted
            // in the waveformDatabase.
            eventArm.change(ev.getEvent(),
                            Status.get(Stage.EVENT_CHANNEL_POPULATION,
                                       Standing.SUCCESS));
            int numWaiting;
            synchronized(eventStatus) {
                numWaiting = eventStatus.getNumWaiting();
            }
            if(numWaiting < EventArm.MIN_WAIT_EVENTS) {
                logger.debug("There are less than "
                        + EventArm.MIN_WAIT_EVENTS
                        + " waiting events.  Telling the eventArm to start up again");
                synchronized(Start.getEventArm()) {
                    Start.getEventArm().notify();
                }
            }
        }
        return numEvents;
    }

    private void startNetwork(EventDbObject ev,
                              EventEffectiveTimeOverlap overlap,
                              NetworkDbObject net) throws Exception {
        // don't bother with network if effective time does no
        // overlap event time
        if(!overlap.overlaps(net.getNetworkAccess().get_attributes())) {
            failLogger.info(NetworkIdUtil.toString(net.getNetworkAccess()
                    .get_attributes()
                    .get_id())
                    + "  The networks effective time does not overlap the event time.");
            return;
        } // end of if ()
        StationDbObject[] stations = networkArm.getSuccessfulStations(net);
        for(int i = 0; i < stations.length; i++) {
            startStation(overlap, net, stations[i], ev);
        }
    }

    private void startStation(EventEffectiveTimeOverlap overlap,
                              NetworkDbObject net,
                              StationDbObject station,
                              EventDbObject ev) throws Exception {
        if(!overlap.overlaps(station.getStation())) {
            failLogger.debug(StationIdUtil.toString(station.getStation()
                    .get_id())
                    + "  The stations effective time does not overlap the event time.");
            return;
        } // end of if ()
        SiteDbObject[] sites = networkArm.getSuccessfulSites(net, station);
        for(int i = 0; i < sites.length; i++) {
            startSite(overlap, net, sites[i], ev);
        }
    }

    private void startSite(EventEffectiveTimeOverlap overlap,
                           NetworkDbObject net,
                           SiteDbObject site,
                           EventDbObject ev) throws Exception {
        if(!overlap.overlaps(site.getSite())) {
            failLogger.debug(SiteIdUtil.toString(site.getSite().get_id())
                    + "  The sites effective time does not overlap the event time: ");
            return;
        } // end of if ()
        ChannelDbObject[] chans = networkArm.getSuccessfulChannels(net, site);
        if(motionVectorArm != null) {
            startChannelGroups(overlap, ev, chans);
        } else {
            // individual local seismograms
            for(int i = 0; i < chans.length; i++) {
                startChannel(overlap, chans[i], ev);
            }
        }
    }

    private void startChannelGroups(EventEffectiveTimeOverlap overlap,
                                    EventDbObject ev,
                                    ChannelDbObject[] chans)
            throws SQLException {
        List overlapList = new ArrayList();
        for(int i = 0; i < chans.length; i++) {
            if(overlap.overlaps(chans[i].getChannel())) {
                overlapList.add(chans[i]);
            } else {
                logger.debug("The channel effective time does not overlap the event time");
            }
        }
        ChannelDbObject[] overlapChans = (ChannelDbObject[])overlapList.toArray(new ChannelDbObject[0]);
        ChannelGroup[] chanGroups = groupChannels(overlapChans, ev.getDbId());
        int evDbId = ev.getDbId();
        Status eventStationInit = Status.get(Stage.EVENT_STATION_SUBSETTER,
                                             Standing.INIT);
        for(int i = 0; i < chanGroups.length; i++) {
            int[] pairIds = new int[3];
            for(int j = 0; j < pairIds.length; j++) {
                for(int k = 0; k < overlapChans.length; k++) {
                    if(ChannelIdUtil.areEqual(overlapChans[k].getChannel()
                            .get_id(), chanGroups[i].getChannels()[j].get_id())) {
                        synchronized(evChanStatus) {
                            pairIds[j] = evChanStatus.put(evDbId,
                                                          overlapChans[k].getDbId(),
                                                          eventStationInit);
                        }
                        break;
                    }
                }
            }
            invokeLaterAsCapacityAllows(new MotionVectorWaveformWorkUnit(pairIds));
            retryIfNeededAndAvailable();
        }
    }

    public ChannelGroup[] groupChannels(ChannelDbObject[] chans, int evDbId)
            throws SQLException {
        Channel[] channels = new Channel[chans.length];
        for(int i = 0; i < channels.length; i++) {
            channels[i] = chans[i].getChannel();
        }
        LinkedList failures = new LinkedList();
        ChannelGroup[] chanGroups = channelGrouper.group(channels, failures);
        Iterator it = failures.iterator();
        Status eventStationReject = Status.get(Stage.EVENT_STATION_SUBSETTER,
                                               Standing.REJECT);
        while(it.hasNext()) {
            Channel failchan = (Channel)it.next();
            failLogger.info(ChannelIdUtil.toString(failchan.get_id())
                    + "  Channel not grouped.");
            for(int k = 0; k < chans.length; k++) {
                if(ChannelIdUtil.areEqual(chans[k].getChannel().get_id(),
                                          failchan.get_id())) {
                    int chanDbId = chans[k].getDbId();
                    synchronized(evChanStatus) {
                        evChanStatus.put(evDbId, chanDbId, eventStationReject);
                    }
                }
            }
        }
        return chanGroups;
    }

    private void startChannel(EventEffectiveTimeOverlap overlap,
                              ChannelDbObject chan,
                              EventDbObject ev) throws Exception {
        if(!overlap.overlaps(chan.getChannel())) {
            logger.debug("The channel effective time does not overlap the event time");
            return;
        } // end of if ()
        // cache the channelInformation.
        int pairId = -1;
        synchronized(evChanStatus) {
            try {
                // getPairId to see if it exists
                evChanStatus.getPairId(ev.getDbId(), chan.getDbId());
            } catch(NotFound e) {// pairId doesn't exist. Putting it in the
                // database.
                pairId = evChanStatus.put(ev.getDbId(),
                                          chan.getDbId(),
                                          Status.get(Stage.EVENT_STATION_SUBSETTER,
                                                     Standing.INIT));
            }
        }
        if(pairId != -1) {
            invokeLaterAsCapacityAllows(new LocalSeismogramWaveformWorkUnit(pairId));
        }
        retryIfNeededAndAvailable();
    }

    private void retryIfNeededAndAvailable() throws SQLException {
        int numInPool = pool.getNumWaiting();
        if(numInPool == 0
                || getNumRetryWaiting() / (double)numInPool < retryPercentage) {
            retryIfAvailable();
        }
    }

    private void retryIfAvailable() throws SQLException {
        WaveformWorkUnit retryUnit = getNextRetry();
        if(retryUnit != null) {
            invokeLaterAsCapacityAllows(retryUnit);
        }
    }

    public EventVectorPair getEventVectorPair(EventChannelPair ecp)
            throws NetworkNotFound {
        try {
            Channel[] chans;
            ChannelDbObject[] chanDb = networkArm.getAllChannelsFromSite(ecp.getChannelDbId());
            chans = new Channel[chanDb.length];
            for(int i = 0; i < chanDb.length; i++) {
                chans[i] = chanDb[i].getChannel();
            }
            ChannelGroup[] groups = channelGrouper.group(chans, new ArrayList());
            ChannelGroup pairGroup = null;
            for(int i = 0; i < groups.length; i++) {
                if(groups[i].contains(ecp.getChannel())) {
                    pairGroup = groups[i];
                    break;
                }
            }
            if(pairGroup == null) {
                ecp.update(Status.get(Stage.EVENT_CHANNEL_POPULATION,
                                      Standing.SYSTEM_FAILURE));
                setStatus(ecp);
                return null;
            }
            int[] pairIds;
            synchronized(evChanStatus) {
                pairIds = evChanStatus.getPairs(ecp.getEvent(), pairGroup);
            }
            EventChannelPair[] pairs = new EventChannelPair[pairIds.length];
            for(int i = 0; i < pairIds.length; i++) {
                synchronized(evChanStatus) {
                    pairs[i] = evChanStatus.get(pairIds[i], this);
                }
            }
            return new EventVectorPair(pairs);
        } catch(SQLException e) {
            GlobalExceptionHandler.handle(e);
        } catch(NotFound e) {
            GlobalExceptionHandler.handle(e);
        }
        return null;
    }

    private Integer getNextRetryId() throws SQLException {
        if(retries.hasNext()) {
            return new Integer(retries.next());
        } else if(corbaFailures.hasNext()) {
            return new Integer(corbaFailures.next());
        } else {
            return null;
        }
    }

    private WaveformWorkUnit getNextRetry() throws SQLException {
        while(true) {
            // keep going until we either get a retry without errors or there
            // are no more, ie return null
            Integer nextPairId = getNextRetryId();
            if(nextPairId == null) {
                return null;
            }
            int pairId = nextPairId.intValue();
            if(motionVectorArm != null) {
                try {
                    EventChannelPair ecp;
                    try {
                        synchronized(evChanStatus) {
                            ecp = evChanStatus.get(pairId, this);
                        }
                    } catch(NotFound e) {
                        handleExceptionGettingRetry("EventChannelStatus get unable to find pair right after it gave it to me",
                                                    pairId,
                                                    e);
                        continue;
                    }
                    try {
                        EventVectorPair ecgp = getEventVectorPair(ecp);
                        if(ecgp == null) {
                            handleExceptionGettingRetry("Unable to get EventVectorPair for EventChannelPair, skipping it",
                                                        pairId,
                                                        new RuntimeException());
                            continue;
                        }
                        int[] pairs;
                        synchronized(evChanStatus) {
                            pairs = evChanStatus.getPairs(ecgp);
                        }
                        return new RetryMotionVectorWaveformWorkUnit(pairs);
                    } catch(NotFound e) {
                        handleExceptionGettingRetry("EventChannelStatus getPairs unable to find vector pairs",
                                                    pairId,
                                                    e);
                        continue;
                    } catch(NetworkNotFound e) {
                        handleExceptionGettingRetry("EventChannelStatus get unable to find network",
                                                    pairId,
                                                    e);
                        continue;
                    }
                } catch(SQLException e) {
                    handleExceptionGettingRetry("Trouble matching up a pair with its waveform group",
                                                pairId,
                                                e);
                    continue;
                }
            } else {
                return new RetryWaveformWorkUnit(pairId);
            }
        }
    }

    private void handleExceptionGettingRetry(String msg, int pairId, Throwable t)
            throws SQLException {
        GlobalExceptionHandler.handle(msg + " pairId=" + pairId, t);
        evChanStatus.setStatus(pairId,
                               Status.get(Stage.EVENT_CHANNEL_POPULATION,
                                          Standing.SYSTEM_FAILURE));
    }

    private void addSuspendedPairsToQueue(int[] pairIds) throws SQLException {
        for(int i = 0; i < pairIds.length; i++) {
            logger.debug("Starting suspended pair " + pairIds[i]);
            WaveformWorkUnit workUnit = null;
            if(localSeismogramArm != null) {
                workUnit = new LocalSeismogramWaveformWorkUnit(pairIds[i]);
            } else {
                try {
                    EventChannelPair ecp;
                    synchronized(evChanStatus) {
                        ecp = evChanStatus.get(pairIds[i], this);
                    }
                    EventVectorPair ecgp = getEventVectorPair(ecp);
                    if(ecgp != null && !usedPairGroups.contains(ecgp)) {
                        usedPairGroups.add(ecgp);
                        int[] pairGroup;
                        synchronized(evChanStatus) {
                            pairGroup = evChanStatus.getPairs(ecgp);
                        }
                        workUnit = new MotionVectorWaveformWorkUnit(pairGroup);
                    }
                } catch(NotFound e) {
                    GlobalExceptionHandler.handle("EventChannelStatus table unable to find pair "
                                                          + pairIds[i]
                                                          + " right after it gave it to me",
                                                  e);
                } catch(NetworkNotFound e) {
                    GlobalExceptionHandler.handle("addSuspendedPairsToQueue: EventChannelStatus get unable to find network "
                                                          + pairIds[i],
                                                  e);
                }
            }
            if(workUnit != null) {
                logger.debug("Adding " + workUnit + " to pool");
                pool.invokeLater(workUnit);
            } else {
                logger.debug("Unable to find work unit for pair");
            }
        }
    }

    private int getNumRetryWaiting() {
        synchronized(retryNumLock) {
            return retryNum;
        }
    }

    /**
     * This method blocks until there is space in the pool for wu to run, then
     * starts its execution.
     */
    private void invokeLaterAsCapacityAllows(WaveformWorkUnit wu) {
        while(pool.getNumWaiting() > poolLineCapacity) {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {}
        }
        pool.invokeLater(wu);
    }

    private void waitForInitialEvent() throws SQLException {
        int next;
        synchronized(eventStatus) {
            next = eventStatus.getNext();
        }
        while(possibleToContinue() && next == -1) {
            logger.debug("Waiting for the first exciting event to show up");
            try {
                synchronized(this) {
                    wait();
                }
            } catch(InterruptedException e) {}
            synchronized(eventStatus) {
                next = eventStatus.getNext();
            }
        }
    }

    public void add(WaveformProcess proc) {
        if(motionVectorArm != null) {
            motionVectorArm.add(new ANDWaveformProcessWrapper(proc));
        } else {
            localSeismogramArm.add(proc);
        }
    }

    protected void processConfig(Element config) throws ConfigurationException {
        if(config.getTagName().equals("waveformVectorArm")) {
            motionVectorArm = new MotionVectorArm();
            channelGrouper = new ChannelGrouper();
        } else {
            localSeismogramArm = new LocalSeismogramArm();
        }
        NodeList children = config.getChildNodes();
        for(int i = 0; i < children.getLength(); i++) {
            if(children.item(i) instanceof Element) {
                Element el = (Element)children.item(i);
                Object sodElement = SodUtil.load(el, PACKAGES);
                if(sodElement instanceof EventStationSubsetter) {
                    eventStationSubsetter = (EventStationSubsetter)sodElement;
                } else if(sodElement instanceof WaveformMonitor) {
                    addStatusMonitor((WaveformMonitor)sodElement);
                } else {
                    if(localSeismogramArm != null) {
                        localSeismogramArm.handle(sodElement);
                    } else {
                        motionVectorArm.handle(sodElement);
                    }
                }
            } // end of if (node instanceof Element)
        } // end of for (intadd i=0; i<children.getSize(); i++)
    }

    public static final String[] PACKAGES = new String[] {"waveformArm",
                                                          "availableData",
                                                          "availableData.vector",
                                                          "eventChannel",
                                                          "eventChannel.vector",
                                                          "eventStation",
                                                          "request",
                                                          "request.vector",
                                                          "requestGenerator",
                                                          "requestGenerator.vector",
                                                          "waveform",
                                                          "waveform.vector",
                                                          "dataCenter"};

    public void addStatusMonitor(WaveformMonitor monitor) {
        statusMonitors.add(monitor);
    }

    public synchronized void setStatus(EventChannelPair ecp) {
        try {
            synchronized(evChanStatus) {
                evChanStatus.setStatus(ecp.getPairId(), ecp.getStatus());
            }
        } catch(SQLException e) {
            GlobalExceptionHandler.handle("Trouble setting the status on an event channel pair",
                                          e);
        }
        synchronized(statusMonitors) {
            Iterator it = statusMonitors.iterator();
            while(it.hasNext()) {
                try {
                    ((WaveformMonitor)it.next()).update(ecp);
                } catch(Exception e) {
                    // oh well, log it and go to next status processor
                    GlobalExceptionHandler.handle("Problem in setStatus", e);
                }
            }
        }
    }

    private EventDbObject popAndGet() {
        synchronized(eventStatus) {
            try {
                int id = eventStatus.getNext();
                if(id != -1)
                    return getEvent(id);
                return null;
            } catch(SQLException e) {
                throw new RuntimeException("Trouble with event db", e);
            }
        }
    }

    private EventDbObject getEvent(int eventDbId) {
        synchronized(eventStatus) {
            try {
                return new EventDbObject(eventDbId,
                                         eventStatus.getEvent(eventDbId));
            } catch(Exception e) {
                throw new RuntimeException("Trouble with event db", e);
            }
        }
    }

    private MicroSecondDate lastEventStartLogTime;

    private interface WaveformWorkUnit extends Runnable {}

    private class LocalSeismogramWaveformWorkUnit implements WaveformWorkUnit {

        public LocalSeismogramWaveformWorkUnit(int pairId) {
            this.pairId = pairId;
        }

        public void run() {
            try {
                ecp = extractEventChannelPair();
                ecp.update(Status.get(Stage.EVENT_STATION_SUBSETTER,
                                      Standing.IN_PROG));
                StringTree accepted = new StringTreeLeaf(this, false);
                try {
                    Station evStation = ecp.getChannel().my_site.my_station;
                    synchronized(eventStationSubsetter) {
                        accepted = eventStationSubsetter.accept(ecp.getEvent(),
                                                                evStation,
                                                                ecp.getCookieJar());
                    }
                } catch(Throwable e) {
                    ecp.update(e, Status.get(Stage.EVENT_STATION_SUBSETTER,
                                             Standing.SYSTEM_FAILURE));
                    failLogger.warn(ecp, e);
                    return;
                }
                if(accepted.isSuccess()) {
                    localSeismogramArm.processLocalSeismogramArm(ecp);
                } else {
                    ecp.update(Status.get(Stage.EVENT_STATION_SUBSETTER,
                                          Standing.REJECT));
                    failLogger.info(ecp + "  " + accepted.toString());
                }
                Status stat = ecp.getStatus();
                if(stat.getStanding() == Standing.CORBA_FAILURE) {
                    corbaFailures.retry(ecp.getPairId());
                } else if(stat.getStanding() == Standing.RETRY) {
                    retries.retry(ecp.getPairId());
                }
            } catch(Throwable t) {
                System.err.println(BIG_ERROR_MSG);
                t.printStackTrace(System.err);
                GlobalExceptionHandler.handle(BIG_ERROR_MSG, t);
            }
        }

        public String toString() {
            return "LocalSeismogramWorkUnit(" + pairId + ")";
        }

        private EventChannelPair extractEventChannelPair() throws Exception {
            try {
                synchronized(evChanStatus) {
                    return evChanStatus.get(pairId, WaveformArm.this);
                }
            } catch(NotFound e) {
                throw new RuntimeException("Not found getting the event and channel ids from the event channel status db for a just gotten pair id.  This shouldn't happen.",
                                           e);
            } catch(SQLException e) {
                throw new RuntimeException("SQL Exception getting the event and channel ids from the event channel status db",
                                           e);
            }
        }

        protected EventChannelPair ecp;

        protected int pairId;
    }

    private class RetryWaveformWorkUnit extends LocalSeismogramWaveformWorkUnit {

        public RetryWaveformWorkUnit(int pairId) {
            super(pairId);
            logger.debug("Retrying on pair id " + pairId);
            synchronized(retryNumLock) {
                retryNum++;
            }
        }

        public void run() {
            synchronized(retryNumLock) {
                retryNum--;
            }
            super.run();
        }
    }

    private class RetryMotionVectorWaveformWorkUnit extends
            MotionVectorWaveformWorkUnit {

        public RetryMotionVectorWaveformWorkUnit(int[] pairId) {
            super(pairId);
            logger.debug("Retrying on pair id " + pairId[0] + " " + pairId[1]
                    + " " + pairId[2]);
            synchronized(retryNumLock) {
                retryNum++;
            }
        }

        public void run() {
            synchronized(retryNumLock) {
                retryNum--;
            }
            super.run();
        }
    }

    private class MotionVectorWaveformWorkUnit implements WaveformWorkUnit {

        public MotionVectorWaveformWorkUnit(int[] pairIds) {
            this.pairIds = pairIds;
        }

        public void run() {
            try {
                EventVectorPair ecp = extractEventVectorPair();
                ecp.update(Status.get(Stage.EVENT_STATION_SUBSETTER,
                                      Standing.IN_PROG));
                StringTree accepted = new StringTreeLeaf(this, false);
                try {
                    Station evStation = ecp.getChannelGroup().getChannels()[0].my_site.my_station;
                    synchronized(eventStationSubsetter) {
                        accepted = eventStationSubsetter.accept(ecp.getEvent(),
                                                                evStation,
                                                                ecp.getCookieJar());
                    }
                } catch(Throwable e) {
                    ecp.update(e, Status.get(Stage.EVENT_STATION_SUBSETTER,
                                             Standing.SYSTEM_FAILURE));
                    failLogger.warn(ecp, e);
                    return;
                }
                if(accepted.isSuccess()) {
                    motionVectorArm.processMotionVectorArm(ecp);
                } else {
                    ecp.update(Status.get(Stage.EVENT_STATION_SUBSETTER,
                                          Standing.REJECT));
                    failLogger.info(ecp + "  " + accepted.toString());
                }
                Status stat = ecp.getStatus();
                // Only make one retry for the whole vector
                if(stat.getStanding() == Standing.CORBA_FAILURE) {
                    corbaFailures.retry(pairIds[0]);
                } else if(stat.getStanding() == Standing.RETRY) {
                    retries.retry(pairIds[0]);
                }
            } catch(Throwable t) {
                System.err.println(BIG_ERROR_MSG);
                t.printStackTrace(System.err);
                GlobalExceptionHandler.handle(BIG_ERROR_MSG, t);
            }
        }

        public String toString() {
            StringBuffer buff = new StringBuffer("MotionVectorWorkUnit(");
            for(int i = 0; i < pairIds.length; i++) {
                buff.append(pairIds[i]);
                buff.append(',');
            }
            return buff.toString();
        }

        private EventVectorPair extractEventVectorPair() throws Exception {
            try {
                EventChannelPair[] pairs = new EventChannelPair[pairIds.length];
                synchronized(evChanStatus) {
                    for(int i = 0; i < pairIds.length; i++) {
                        pairs[i] = evChanStatus.get(pairIds[i],
                                                    WaveformArm.this);
                    }
                }
                return new EventVectorPair(pairs);
            } catch(NotFound e) {
                throw new RuntimeException("Not found getting the event and channel ids from the event channel status db for a just gotten pair id.  This shouldn't happen.",
                                           e);
            } catch(SQLException e) {
                throw new RuntimeException("SQL Exception getting the event and channel ids from the event channel status db",
                                           e);
            }
        }

        private int[] pairIds;
    }

    private static final String BIG_ERROR_MSG = "An exception occured that would've croaked a waveform worker thread!  These types of exceptions are certainly possible, but they shouldn't be allowed to percolate this far up the stack.  If you are one of those esteemed few working on SOD, it behooves you to attempt to trudge down the stack trace following this message and make certain that whatever threw this exception is no longer allowed to throw beyond its scope.  If on the other hand, you are a user of SOD it would be most appreciated if you would send an email containing the text immediately following this mesage to sod@seis.sc.edu";

    private boolean finished = false;

    private WorkerThreadPool pool;

    private EventStationSubsetter eventStationSubsetter = new PassEventStation();

    private LocalSeismogramArm localSeismogramArm = null;

    private MotionVectorArm motionVectorArm = null;

    private NetworkArm networkArm = null;

    private EventArm eventArm = null;

    private JDBCEventStatus eventStatus;

    private JDBCEventChannelStatus evChanStatus;

    private JDBCRetryQueue corbaFailures, retries;

    private ChannelGrouper channelGrouper;

    private List usedPairGroups = new ArrayList();

    private double retryPercentage = .02;// 2 percent of the pool will be

    // Amount of time after the run has ended that we retry Server based
    // failures
    private TimeInterval SERVER_RETRY_DELAY;

    private static Logger logger = Logger.getLogger(WaveformArm.class);

    private static final org.apache.log4j.Logger failLogger = org.apache.log4j.Logger.getLogger("Fail.WaveformArm");

    private Set statusMonitors = Collections.synchronizedSet(new HashSet());

    private int poolLineCapacity = 100, retryNum;

    private Object retryNumLock = new Object();

    public JDBCRetryQueue getRetryQueue() {
        return retries;
    }
}