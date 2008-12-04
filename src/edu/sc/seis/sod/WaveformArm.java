package edu.sc.seis.sod;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.hibernate.LockMode;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import edu.iris.Fissures.IfEvent.NoPreferredOrigin;
import edu.iris.Fissures.IfNetwork.NetworkNotFound;
import edu.iris.Fissures.model.MicroSecondDate;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.network.NetworkIdUtil;
import edu.sc.seis.fissuresUtil.cache.CacheNetworkAccess;
import edu.sc.seis.fissuresUtil.cache.EventUtil;
import edu.sc.seis.fissuresUtil.cache.WorkerThreadPool;
import edu.sc.seis.fissuresUtil.chooser.ClockUtil;
import edu.sc.seis.fissuresUtil.exceptionHandler.GlobalExceptionHandler;
import edu.sc.seis.sod.hibernate.SodDB;
import edu.sc.seis.sod.hibernate.StatefulEvent;
import edu.sc.seis.sod.hibernate.StatefulEventDB;
import edu.sc.seis.sod.process.waveform.WaveformProcess;
import edu.sc.seis.sod.process.waveform.vector.ANDWaveformProcessWrapper;
import edu.sc.seis.sod.status.OutputScheduler;
import edu.sc.seis.sod.status.waveformArm.WaveformMonitor;
import edu.sc.seis.sod.subsetter.EventEffectiveTimeOverlap;
import edu.sc.seis.sod.subsetter.eventStation.EventStationSubsetter;
import edu.sc.seis.sod.subsetter.eventStation.PassEventStation;

public class WaveformArm implements Arm {

    public WaveformArm(Element config,
                       EventArm eventArm,
                       NetworkArm networkArm,
                       int threadPoolSize,
                       boolean restartSuspended) throws Exception {
        initDb();
        processConfig(config);
        this.networkArm = networkArm;
        this.eventArm = eventArm;
        pool = new Thread[threadPoolSize];
        for(int i = 0; i < pool.length; i++) {
            pool[i] = new WaveformProcessor();
        }
        this.restartSuspended = restartSuspended;
    }

    private void initDb() throws SQLException {
        sodDb = SodDB.getSingleton();
        logger.info("SodDB in WaveformArm:" + sodDb);
        eventDb = StatefulEventDB.getSingleton();
    }

    public boolean isActive() {
        return !finished;
    }

    public String getName() {
        return "WaveformArm";
    }

    public void run() {
        for(int i = 0; i < pool.length; i++) {
            pool[i].start();
        }
        try {
            if(restartSuspended) {
                sodDb.reopenSuspendedEventChannelPairs(Start.getRunProps()
                                                               .getEventChannelPairProcessing(),
                                                       localSeismogramArm == null);
            }
            // check for events that are "in progress" due to halt or reset
            populateEventChannelDb(Standing.IN_PROG);
            int sleepTime = 5;
            TimeInterval logInterval = new TimeInterval(10, UnitImpl.MINUTE);
            logger.debug("will populateEventChannelDb, then sleep "
                    + sleepTime
                    + " sec between each try to process successful events, log interval is "
                    + logInterval);
            lastEventStartLogTime = ClockUtil.now();
            while(!Start.isArmFailure()
                    && WaveformProcessor.getProcessorsWorking() > 0) {
                try {
                    logger.debug("Sleeping while waiting for waveform processors.");
                    getWaveformProcessorSync().wait();
                } catch(InterruptedException e) {}
            }
            logger.info("Lo!  I am weary of my wisdom, like the bee that hath gathered too much\n"
                    + "honey; I need hands outstretched to take it.");
        } catch(Throwable e) {
            Start.armFailure(this, e);
        }
        finished = true;
        if (Start.getRunProps().checkpointPeriodically()) {
            new PeriodicCheckpointer().run();
        }
        synchronized(OutputScheduler.getDefault()) {
            OutputScheduler.getDefault().notify();
        }
    }

    boolean possibleToContinue() {
        return (eventArm.isActive() || isActive()) && !Start.isArmFailure();
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
        while(true) {
            int numWorkUnits = sodDb.getNumWorkUnits(Standing.INIT);
            if(numWorkUnits > WaveformArm.MIN_WORK_UNIT_FOR_SLEEP) {
                logger.debug("ASDF numWorkUnits > min for sleep, waiting on processors");
                // plenty of work, so sleep,
                // but wake if processors run out
                synchronized(getWaveformProcessorSync()) {
                    try {
                        getWaveformProcessorSync().notifyAll();
                        getWaveformProcessorSync().wait();
                    } catch(InterruptedException e) {}
                }
            } else if(numEvents == 0 && eventArm.isActive()) {
                logger.debug("ASDF no events, wait on event arm");
                // not enough work, but there were also no new events, wait on
                // event arm
                synchronized(eventArm.getWaveformArmSync()) {
                    try {
                        eventArm.getWaveformArmSync().notifyAll();
                        if (eventArm.isActive()) {
                            logger.debug("sleeping for eventarm");
                            eventArm.getWaveformArmSync().wait();
                        }
                        logger.debug("done sleeping for eventarm");
                    } catch(InterruptedException e) {}
                }
                return;
            } else {
                // not enough work, but events worth processing, so go back to
                // work
                return;
            }
        }
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
    protected int populateEventChannelDb(Standing standing)  {
        int numEvents = 0;
        for(StatefulEvent ev = eventDb.getNext(standing); ev != null; ev = eventDb.getNext(standing)) {
            logger.debug("Work on event: " + ev.getDbid() + " "
                    + EventUtil.getEventInfo(ev));
            ev.setStatus(Status.get(Stage.EVENT_CHANNEL_POPULATION,
                                    Standing.IN_PROG));
            eventDb.getSession().saveOrUpdate(ev);
            eventDb.commit();
            // refresh event to put back in new session
            eventDb.getSession().load(ev, ev.getDbid());
            numEvents++;
            EventEffectiveTimeOverlap overlap;
            try {
                if(ev.get_preferred_origin().getOriginTime() == null) {
                    throw new RuntimeException("otime is null "
                            + ev.get_preferred_origin().getLocation());
                }
                overlap = new EventEffectiveTimeOverlap(ev);
            } catch(NoPreferredOrigin e) {
                throw new RuntimeException("Should never happen...", e);
            }
            CacheNetworkAccess[] networks;
            networks = networkArm.getSuccessfulNetworks();
            for(int i = 0; i < networks.length; i++) {
                if(overlap.overlaps(networks[i].get_attributes())) {
                    EventNetworkPair p = new EventNetworkPair(ev,
                                                              networks[i],
                                                              Status.get(Stage.EVENT_CHANNEL_POPULATION,
                                                                         Standing.INIT));
                    sodDb.put(p);
                } else {
                    failLogger.info("Network "
                            + NetworkIdUtil.toStringNoDates(networks[i].get_attributes())
                            + " does not overlap event " + ev);
                }
            }
            // set the status of the event to be SUCCESS implying that
            // that all the network information for this particular event is
            // inserted
            // in the waveformDatabase.
            ev.setStatus(Status.get(Stage.EVENT_CHANNEL_POPULATION,
                                    Standing.SUCCESS));
            eventDb.commit();
            // wake up the workers in case they are asleep
            synchronized(getWaveformProcessorSync()) {
                getWaveformProcessorSync().notifyAll();
            }
            eventArm.change(ev);
            int numWaiting = eventDb.getNumWaiting();
            if(numWaiting < EventArm.MIN_WAIT_EVENTS) {
                logger.debug("There are less than "
                        + EventArm.MIN_WAIT_EVENTS
                        + " waiting events.  Telling the eventArm to start up again");
                synchronized(Start.getEventArm().getWaveformArmSync()) {
                    Start.getEventArm().getWaveformArmSync().notifyAll();
                }
            }
        }
        eventDb.commit();
        return numEvents;
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

    public synchronized void setStatus(CookieEventPair ecp) {
        synchronized(statusMonitors) {
            Iterator it = statusMonitors.iterator();
            while(it.hasNext()) {
                try {
                    if(ecp instanceof EventChannelPair) {
                        ((WaveformMonitor)it.next()).update((EventChannelPair)ecp);
                    } else if(ecp instanceof EventVectorPair) {
                        ((WaveformMonitor)it.next()).update((EventVectorPair)ecp);
                    } else if(ecp instanceof EventStationPair) {
                        ((WaveformMonitor)it.next()).update((EventStationPair)ecp);
                    }
                } catch(Exception e) {
                    // oh well, log it and go to next status processor
                    GlobalExceptionHandler.handle("Problem in setStatus", e);
                }
            }
        }
    }

    public synchronized void setStatus(EventNetworkPair ecp) {
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

    public double getRetryPercentage() {
        return retryPercentage;
    }

    private MicroSecondDate lastEventStartLogTime;

    public static final String BIG_ERROR_MSG = "An exception occured that would've croaked a waveform worker thread!  These types of exceptions are certainly possible, but they shouldn't be allowed to percolate this far up the stack.  If you are one of those esteemed few working on SOD, it behooves you to attempt to trudge down the stack trace following this message and make certain that whatever threw this exception is no longer allowed to throw beyond its scope.  If on the other hand, you are a user of SOD it would be most appreciated if you would send an email containing the text immediately following this mesage to sod@seis.sc.edu";

    private boolean finished = false;

    private boolean restartSuspended;

    private Thread[] pool;

    private EventStationSubsetter eventStationSubsetter = new PassEventStation();

    private LocalSeismogramArm localSeismogramArm = null;

    private MotionVectorArm motionVectorArm = null;

    private NetworkArm networkArm = null;

    private EventArm eventArm = null;

    private SodDB sodDb;

    private StatefulEventDB eventDb;

    private double retryPercentage = .02;// 2 percent of the pool will be

    private static int MIN_WORK_UNIT_FOR_SLEEP = 100;

    private static Logger logger = Logger.getLogger(WaveformArm.class);

    private static final org.apache.log4j.Logger failLogger = org.apache.log4j.Logger.getLogger("Fail.WaveformArm");

    private Set statusMonitors = Collections.synchronizedSet(new HashSet());

    private final Object waveformProcessorSync = new Object();

    int retryNum;

    public Object getWaveformProcessorSync() {
        return waveformProcessorSync;
    }
}
