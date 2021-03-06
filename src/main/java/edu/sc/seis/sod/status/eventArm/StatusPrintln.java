/**
 * StatusPrintln.java
 *
 * @author Created by Omnicore CodeGuide
 */

package edu.sc.seis.sod.status.eventArm;

import org.w3c.dom.Element;

import edu.sc.seis.fissuresUtil.cache.CacheEvent;
import edu.sc.seis.fissuresUtil.cache.EventUtil;
import edu.sc.seis.sod.Status;

public class StatusPrintln implements EventMonitor{
    public StatusPrintln(Element config){}

    public void setArmStatus(String status) {
        System.out.println("Event Arm: " + status);
    }

    public void change(CacheEvent event, Status status) {
        System.out.println(status + ": " + EventUtil.getEventInfo(event));
    }
}
