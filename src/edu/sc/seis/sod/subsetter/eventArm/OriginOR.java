package edu.sc.seis.sod.subsetter.eventArm;

import edu.sc.seis.sod.*;
import java.util.*;
import org.w3c.dom.*;
import edu.iris.Fissures.IfEvent.*;
import edu.iris.Fissures.event.*;
import edu.iris.Fissures.*;

/**
 * OriginOR.java
 *
 *
 * Created: Thu Mar 14 14:02:33 2002
 *
 * @author <a href="mailto:">Philip Crotwell</a>
 * @version
 */

public class OriginOR 
    extends EventLogicalSubsetter 
    implements OriginSubsetter {
    
    /**
     * Creates a new <code>OriginOR</code> instance.
     *
     * @param config an <code>Element</code> value
     * @exception ConfigurationException if an error occurs
     */
    public OriginOR (Element config) throws ConfigurationException {
	super(config);
    }

    /**
     * Describe <code>accept</code> method here.
     *
     * @param event an <code>EventAccessOperations</code> value
     * @param e an <code>Origin</code> value
     * @param cookies a <code>CookieJar</code> value
     * @return a <code>boolean</code> value
     * @exception Exception if an error occurs
     */
    public boolean accept(EventAccessOperations event, Origin e,  CookieJar cookies) throws Exception{
	Iterator it = filterList.iterator();
	while (it.hasNext()) {
	    OriginSubsetter filter = (OriginSubsetter)it.next();
	    if (filter.accept(event, e, cookies)) {
		return true;
	    }
	}
	return false;
    }

}// OriginOR
