/**
 * PeriodicAction.java
 *
 * @author Created by Omnicore CodeGuide
 */

package edu.sc.seis.sod.status;

import edu.iris.Fissures.model.MicroSecondDate;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.model.UnitImpl;
import edu.sc.seis.fissuresUtil.chooser.ClockUtil;
import edu.sc.seis.fissuresUtil.exceptionHandler.GlobalExceptionHandler;
import java.util.Timer;
import java.util.TimerTask;

public abstract class PeriodicAction{
    public abstract void act();

    public void actIfPeriodElapsed(){
        synchronized(schedulingLock) {
            if(!ClockUtil.now().subtract(lastAct).lessThan(ACTION_INTERVAL)){
                actNow();
            }else if(!scheduled){
                t.schedule(new ScheduledActor(), (int)ACTION_INTERVAL.convertTo(UnitImpl.MILLISECOND).get_value());
                scheduled = true;
            }
        }
    }

    private class ScheduledActor extends TimerTask{
        public void run() { actNow();  }
    }

    private void actNow(){
        try{
            act();
        }catch(Throwable t){
            GlobalExceptionHandler.handle("Trouble running periodic action", t);
        }
        synchronized(schedulingLock){
            lastAct = ClockUtil.now();
            scheduled = false;
        }
    }

    private boolean scheduled = false;
    private static final TimeInterval ACTION_INTERVAL = new TimeInterval(.5, UnitImpl.MINUTE);
    private MicroSecondDate lastAct = ClockUtil.now().subtract(ACTION_INTERVAL);
    private Object schedulingLock = new Object();
    private static Timer t = new Timer();
}

