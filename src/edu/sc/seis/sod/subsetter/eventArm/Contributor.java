package edu.sc.seis.sod.subsetter.eventArm;

import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfEvent.EventAttr;
import edu.iris.Fissures.IfEvent.Origin;
import edu.sc.seis.sod.SodUtil;
import org.w3c.dom.Element;

/**
 * This tag is used to specify the value of the catalog.
 *<pre>
 * &lt;contributor&gt;&lt;value&gt;NEIC&lt;/value&gt;&lt;/contributor&gt;
 *</pre>
 */


public class Contributor implements OriginSubsetter{
    public Contributor (Element config){this.config = config;}

    /**
     * returns true if the contributor of the origin is same as the corresponding
     * contributor specified in the configuration file.
     */
    public boolean accept(EventAccessOperations event, EventAttr eventAttr, Origin origin) {
        if(origin.contributor.equals(getContributor())) return true;
        return false;
    }

    /**
     * returns the contributor.
     */
    public String getContributor() {
        return SodUtil.getNestedText(config);
    }

    private Element config;
}// Contributor
