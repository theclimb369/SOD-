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
 * This subsetter is used to specify a sequence of EventStationSubsetters. This subsetter is accepted when even one 
 * of the subsetters forming the sequence is accepted. If all the subsetters in the sequence are not accepted then
 * the eventStationOR is not accepted.
 *	&lt;eventStationOR&gt;
 *		&lt;phaseExists&gt;
 * 			&lt;modelName&gt;prem&lt;/modelName&gt;
 *			&lt;phaseName&gt;ttp&lt;/phaseName&gt;
 *		&lt;/phaseExists&gt;
 *		&lt;phaseInteraction&gt;
 *			&lt;modelName&gt;prem&lt;/modelName&gt;
 *			&lt;phaseName&gt;PcP&lt;/phaseName&gt;
 *			&lt;interactionStyle&gt;PATH&lt;/interactionStyle&gt;
 *			&lt;interactionNumber&gt;1&lt;/interactionNumber&gt;
 *			&lt;relative&gt;
 *				&lt;reference&gt;EVENT&lt;/reference&gt;
 *				&lt;depthRange&gt;
 *					&lt;unitRange&gt;
 *						&lt;unit&gt;KILOMETER&lt;/unit&gt;
 *						&lt;min&gt;-1000&lt;/min&gt;
 *						&lt;max&gt;1000&lt;/max&gt;
 *					&lt;/unitRange&gt;
 *				&lt;/depthRange&gt;
 *				&lt;distanceRange&gt;
 *					&lt;unit&gt;DEGREE&lt;/unit&gt;
 *					&lt;min&gt;60&lt;/min&gt;
 *					&lt;max&gt;70&lt;/max&gt;
 *				&lt;/distanceRange&gt;
 *			&lt;/relative&gt;
 *		&lt;/phaseInteraction&gt;
 *	&lt;/eventStationOR&gt;
 */


public class EventStationOR 
    extends  WaveFormLogicalSubsetter 
    implements EventStationSubsetter {
    
    /**
     * Creates a new <code>EventStationOR</code> instance.
     *
     * @param config an <code>Element</code> value
     * @exception ConfigurationException if an error occurs
     */
    public EventStationOR (Element config) throws ConfigurationException {
	super(config);
    }

    /**
     * Describe <code>accept</code> method here.
     *
     * @param o an <code>EventAccessOperations</code> value
     * @param network a <code>NetworkAccess</code> value
     * @param station a <code>Station</code> value
     * @param cookies a <code>CookieJar</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    public boolean accept(EventAccessOperations o, NetworkAccess network, Station station,  CookieJar cookies)
	throws Exception{
	Iterator it = filterList.iterator();
	
	while(it.hasNext()) {
	    EventStationSubsetter filter = (EventStationSubsetter)it.next();
	    if (filter.accept(o, network, station, cookies)) {
		return true;
	    }
	}
	return false;
    }

}// EventStationOR
