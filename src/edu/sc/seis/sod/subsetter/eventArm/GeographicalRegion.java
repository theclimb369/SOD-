package edu.sc.seis.sod.subsetter.eventArm;



import java.util.*;
import org.w3c.dom.*;
import edu.iris.Fissures.IfEvent.*;
import edu.iris.Fissures.event.*;
import edu.iris.Fissures.*;

/**
 *<pre>
 * &lt;geographicalRegion&gt;10 20 30 40&lt;/geographicalRegion&gt;
 *</pre>
 */

public class GeographicalRegion  extends FlinnEngdahlRegion{
    /**
     * Creates a new <code>GeographicalRegion</code> instance.
     *
     * @param config an <code>Element</code> value
     */
    public GeographicalRegion (Element config){
	super(config);
    }

    /**
     * Describe <code>getType</code> method here.
     *
     * @return a <code>FlinnEngdahlType</code> value
     */
    public FlinnEngdahlType getType() {

	return FlinnEngdahlType.from_int(1);
    }
    
}// GeographicalRegion
