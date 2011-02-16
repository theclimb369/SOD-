package edu.sc.seis.sod;

import java.util.List;

import edu.iris.Fissures.IfEvent.NoPreferredOrigin;
import edu.iris.Fissures.model.MicroSecondDate;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.network.NetworkAttrImpl;
import edu.iris.Fissures.network.NetworkIdUtil;
import edu.sc.seis.fissuresUtil.cache.EventUtil;
import edu.sc.seis.fissuresUtil.chooser.ClockUtil;
import edu.sc.seis.fissuresUtil.exceptionHandler.GlobalExceptionHandler;
import edu.sc.seis.sod.hibernate.SodDB;
import edu.sc.seis.sod.hibernate.StatefulEvent;
import edu.sc.seis.sod.hibernate.StatefulEventDB;
import edu.sc.seis.sod.status.OutputScheduler;
import edu.sc.seis.sod.subsetter.EventEffectiveTimeOverlap;

public class WaveformArm extends Thread implements Arm {

    public WaveformArm(int nextProcessorNum, AbstractWaveformRecipe waveformRecipe) {
        super("WaveformArm " + nextProcessorNum);
        this.recipe = waveformRecipe;
        this.processorNum = nextProcessorNum;
    }


    boolean possibleToContinue() {
        return Start.getEventArm().isActive() && ! Start.isArmFailure();
    }
    
    public void run() {
        try {
            while(true) {
                AbstractEventPair next = getNext();
                while(next == null
                        && (possibleToContinue()
                                || SodDB.getSingleton().getNumWorkUnits(Standing.RETRY) != 0 || SodDB.getSingleton()
                                .getNumWorkUnits(Standing.IN_PROG) != 0  || SodDB.getSingleton()
                                .getNumWorkUnits(Standing.INIT) != 0)) {
                    logger.debug("Processor waiting for work unit to show up");
                    try {
                        // sleep, but wake up if eventArm does notifyAll()
                        logger.debug("waiting on event arm");
                        synchronized(Start.getEventArm()) {
                            Start.getEventArm().notifyAll();
                        }
                            if(possibleToContinue()) {
                                synchronized(Start.getEventArm().getWaveformArmSync()) {
                                    // close db connection as we don't need it for next 2 minutes
                                    SodDB.rollback();
                                    // wake up every 2 minutes in case there is retrys to process
                                    Start.getEventArm().getWaveformArmSync().wait(2*60*1000);
                                }
                            }
                    } catch(InterruptedException e) {}
                    logger.debug("done waiting on event arm");
                    next = getNext();
                }
                if(next == null) {
                    // lets sleep for a couple of seconds just to make sure
                    // in case another thread has the  last/only event-network
                    // and we can help out once the stations are retrieved
                    for (int i = 0; i < 5; i++) {
                        next = getNext();
                        if (next != null) {
                            break;
                        }
                        Thread.sleep(1000);
                    }
                }
                if(next != null) {
                    processorStartWork();
                    try {
                        next.run();
                    } catch(Throwable t) {
                        SodDB.rollback();
                        next.update(t, Status.get(Stage.EVENT_CHANNEL_POPULATION, Standing.SYSTEM_FAILURE));
                    }
                    SodDB.commit();
                    processorFinishWork();
                } else {
                    // nothing to do in db, not possible to continue
                    logger.debug("No work to do, quiting processing: " + possibleToContinue()
                            + " " + (SodDB.getSingleton().getNumWorkUnits(Standing.RETRY) != 0) + " "
                            + (SodDB.getSingleton().getNumWorkUnits(Standing.IN_PROG) != 0));
                    return;
                }
            }
        } catch(Throwable t) {
            // just in case...
            GlobalExceptionHandler.handle(t);
            Start.armFailure(this, t);
        } finally {
            active = false;
            synchronized(OutputScheduler.getDefault()) {
                OutputScheduler.getDefault().notify();
            }
        }
    }

    
    public boolean isActive() {
        return active;
    }
    
    boolean active = true;
    
    protected static synchronized AbstractEventPair getNext() {
        double retryRandom = Math.random();
        AbstractEventChannelPair ecp = null;
        if(retryRandom < getRetryPercentage() ) {
            // try a retry
            ecp = SodDB.getSingleton().getNextRetryECPFromCache();
            if (ecp != null) {
                return ecp;
            }
        }
        if (retryRandom > ecpPercentage) {
            // random not in small, so try memory esp or enp first
            if (SodDB.getSingleton().isESPTodo()) {
                EventStationPair esp = SodDB.getSingleton().getNextESPFromCache();
                if(esp != null) {
                    esp.update(Status.get(Stage.EVENT_CHANNEL_POPULATION, Standing.IN_PROG));
                    SodDB.commit();
                    SodDB.getSession().update(esp);
                    return esp;
                }
            }
            // no e-station try e-network
            if (SodDB.getSingleton().isENPTodo()) {
                EventNetworkPair enp = SodDB.getSingleton().getNextENPFromCache();
                if(enp != null) {
                    enp.update(Status.get(Stage.EVENT_CHANNEL_POPULATION, Standing.IN_PROG));
                    SodDB.commit();
                    // reattach to new session
                    SodDB.getSession().update(enp);
                    return enp;
                }
            }
        }
        // normal operation is to get esp from the db, then process all ecp within the station
        // instead of going to the database for each ecp. So only need to check for ecps occassionally
        // mainly to pick up orphans in the event of a crash
        // we do this  by trying if the random is less than the ecpPercentage or if we have
        // have found an ecp in the db within the last ECP_WINDOW
        // this cuts down on useless db acccesses
        if((retryRandom < ecpPercentage || (ClockUtil.now().subtract(lastECP).lessThan(ECP_WINDOW))) ) {
            ecp = SodDB.getSingleton().getNextECP();
            if(ecp != null) {
                ecp.update(Status.get(Stage.EVENT_CHANNEL_POPULATION, Standing.IN_PROG));
                SodDB.commit();
                ecp = (AbstractEventChannelPair)SodDB.getSession().get(ecp.getClass(), ecp.getDbid());
                lastECP = ClockUtil.now();
                return ecp;
            }
        }
        // no ecp/evp try e-station
        EventStationPair esp = SodDB.getSingleton().getNextESP();
        if(esp != null) {
            esp.update(Status.get(Stage.EVENT_CHANNEL_POPULATION, Standing.IN_PROG));
            SodDB.commit();
            SodDB.getSession().update(esp);
            return esp;
        }
        // no e-station try e-network
        EventNetworkPair enp = SodDB.getSingleton().getNextENP();
        if(enp != null) {
            enp.update(Status.get(Stage.EVENT_CHANNEL_POPULATION, Standing.IN_PROG));
            SodDB.commit();
            // reattach to new session
            SodDB.getSession().update(enp);
            return enp;
        }
        // go get more events to make e-net pairs
        StatefulEvent ev = StatefulEventDB.getSingleton().getNext(Standing.INIT);
        if (ev != null) {
            createEventNetworkPairs(ev);
            enp = SodDB.getSingleton().getNextENP();
            if(enp != null) {
                enp.update(Status.get(Stage.EVENT_CHANNEL_POPULATION, Standing.IN_PROG));
                SodDB.commit();
                // reattach to new session
                SodDB.getSession().update(enp);
                return enp;
            }
        }
        // really nothing doing, might as well work on a retry if one is ready?
        return SodDB.getSingleton().getNextRetryECPFromCache();
    }

    protected static MicroSecondDate lastECP = ClockUtil.now(); 
    
    protected static void createEventNetworkPairs(StatefulEvent ev) {
        logger.debug("Work on event: " + ev.getDbid() + " "
                + EventUtil.getEventInfo(ev));
        StatefulEventDB eventDb = StatefulEventDB.getSingleton();
        SodDB sodDb = SodDB.getSingleton();
        ev.setStatus(Status.get(Stage.EVENT_CHANNEL_POPULATION,
                                Standing.IN_PROG));
        eventDb.getSession().saveOrUpdate(ev);
        eventDb.commit();
        // refresh event to put back in new session
        eventDb.getSession().load(ev, ev.getDbid());
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
        List<NetworkAttrImpl> networks = Start.getNetworkArm().getSuccessfulNetworks();
        for (NetworkAttrImpl net : networks) {
            if(overlap.overlaps(net)) {
                EventNetworkPair p = new EventNetworkPair(ev,
                                                          net,
                                                          Status.get(Stage.EVENT_CHANNEL_POPULATION,
                                                                     Standing.INIT));
                sodDb.put(p);
            } else {
                failLogger.info("Network "
                        + NetworkIdUtil.toStringNoDates(net)
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
        Start.getEventArm().change(ev);
        int numWaiting = eventDb.getNumWaiting();
        if(numWaiting < EventArm.MIN_WAIT_EVENTS) {
            logger.debug("There are less than "
                    + EventArm.MIN_WAIT_EVENTS
                    + " waiting events.  Telling the eventArm to start up again");
            synchronized(Start.getEventArm()) {
                Start.getEventArm().notifyAll();
            }
        }
    }
    
    public int getProcessorNum() {
        return processorNum;
    }
    
    AbstractWaveformRecipe recipe;

    private int processorNum;
    
    boolean lastWorkWasEvent = false;
    
    public static int getProcessorsWorking() {
        return processorsWorking;
    }

    static int processorsWorking = 0;

    static void processorStartWork() {
        processorsWorking++;
    }

    static void processorFinishWork() {
        processorsWorking--;
    }

    static int usedProcessorNum = 0;

    static int nextProcessorNum() {
        return usedProcessorNum++;
    }

    private static double getRetryPercentage() {
        return retryPercentage;
    }
    
    /** percent of the pool that will be retries */
    private static double retryPercentage = .01; 
    
    private static double ecpPercentage = .00001; // most processing uses esp from db, only use ecp if crash

    private static TimeInterval ECP_WINDOW = new TimeInterval(5, UnitImpl.MINUTE);
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WaveformArm.class);
    
    private static final org.slf4j.Logger failLogger = org.slf4j.LoggerFactory.getLogger("Fail.WaveformArm");

}
