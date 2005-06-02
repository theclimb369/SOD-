package edu.sc.seis.sod.subsetter.site;

import java.util.Iterator;
import org.w3c.dom.Element;
import edu.iris.Fissures.IfNetwork.NetworkAccess;
import edu.iris.Fissures.IfNetwork.Site;
import edu.sc.seis.sod.ConfigurationException;

public final class SiteOR extends SiteLogicalSubsetter implements SiteSubsetter {

    public SiteOR(Element config) throws ConfigurationException {
        super(config);
    }

    public boolean accept(Site e, NetworkAccess network) throws Exception {
        Iterator it = subsetters.iterator();
        while(it.hasNext()) {
            SiteSubsetter filter = (SiteSubsetter)it.next();
            if(filter.accept(e, network)) { return true; }
        }
        return false;
    }
}// SiteOR
