package edu.sc.seis.sod.subsetter.waveFormArm;

import edu.sc.seis.sod.*;

import edu.iris.Fissures.IfEvent.*;
import edu.iris.Fissures.event.*;
import edu.iris.Fissures.IfNetwork.*;
import edu.iris.Fissures.network.*;

import edu.iris.Fissures.IfSeismogramDC.*;

import org.w3c.dom.*;

/**
 * FullCoverage.java
 *
 *
 * Created: Tue Apr  9 10:27:12 2002
 *
 * @author <a href="mailto:">Srinivasa Telukutla</a>
 * @version
 */

public class FullCoverage implements AvailableDataSubsetter, SodElement{
    /**
     * Creates a new <code>FullCoverage</code> instance.
     *
     * @param config an <code>Element</code> value
     */
    public FullCoverage (Element config){
	
    }
    
    /**
     * Describe <code>accept</code> method here.
     *
     * @param event an <code>EventAccessOperations</code> value
     * @param network a <code>NetworkAccess</code> value
     * @param channel a <code>Channel</code> value
     * @param original a <code>RequestFilter[]</code> value
     * @param available a <code>RequestFilter[]</code> value
     * @param cookies a <code>CookieJar</code> value
     * @return a <code>boolean</code> value
     */
    public boolean accept(EventAccessOperations event, 
			  NetworkAccess network, 
			  Channel channel, 
			  RequestFilter[] original, 
			  RequestFilter[] available, 
			  CookieJar cookies) {

	return true;
    }
    
}// FullCoverage
