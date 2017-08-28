package edu.sc.seis.sod.subsetter.network;

import org.w3c.dom.Element;

import edu.sc.seis.seisFile.fdsnws.stationxml.Network;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeLeaf;

/**
 * NetworkAttrName.java
 * sample xml file
 * <pre>
 * &lt;networkAttrName&gt;&lt;value&gt;somename*lt;/value&gt;&lt;/networkAttrName&gt;
 * </pre>
 *
 * Created: Thu Mar 14 14:02:33 2002
 *
 * @author Philip Crotwell
 */
@Deprecated
public class NetworkName implements NetworkSubsetter {

    public NetworkName (Element config) throws ConfigurationException {
        this.config = config;
    }

    public StringTree accept(Network net) {
        return new StringTreeLeaf(this, net.getDescription().equals(SodUtil.getNestedText(config)));
    }

    private Element config;
    
}// NetworkAttrName
