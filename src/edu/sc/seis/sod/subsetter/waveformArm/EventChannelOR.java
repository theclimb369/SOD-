package edu.sc.seis.sod.subsetter.waveFormArm;

import edu.sc.seis.sod.*;
import java.util.*;
import org.w3c.dom.*;
import edu.iris.Fissures.IfNetwork.*;
import edu.iris.Fissures.network.*;
import edu.iris.Fissures.IfEvent.*;
import edu.iris.Fissures.network.*;
import edu.iris.Fissures.*;

/**
 * This subsetter is used to specify a sequence of EventChannelSubsetters. This subsetter is accepted when even one 
 * of the subsetters forming the sequence is accepted. If all the subsetters in the sequence are not accepted then
 * the eventChannelOR is not accepted.
 * &lt;eventChannelOR&gt;
 * &lt;/eventChannelOR&gt;
 */

public class EventChannelOR 
    extends  WaveFormLogicalSubsetter 
    implements EventChannelSubsetter {
    
    /**
     * Creates a new <code>EventChannelOR</code> instance.
     *
     * @param config an <code>Element</code> value
     * @exception ConfigurationException if an error occurs
     */
    public EventChannelOR (Element config) throws ConfigurationException {
	super(config);
    }

    /**
     * Describe <code>accept</code> method here.
     *
     * @param o an <code>EventAccessOperations</code> value
     * @param network a <code>NetworkAccess</code> value
     * @param channel a <code>Channel</code> value
     * @param cookies a <code>CookieJar</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    public boolean accept(EventAccessOperations o, NetworkAccess network, Channel channel,  CookieJar cookies)
	throws Exception{
	Iterator it = filterList.iterator();
	while (it.hasNext()) {
	    EventChannelSubsetter filter = (EventChannelSubsetter)it.next();
	    if (!filter.accept(o, network, channel, cookies)) {
		return false;
	    }
	}
	return true;
    }

}// EventChannelOR
