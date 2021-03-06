package edu.sc.seis.sod.subsetter.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import edu.iris.Fissures.TimeRange;
import edu.iris.Fissures.network.NetworkAttrImpl;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeLeaf;
import edu.sc.seis.sod.subsetter.EffectiveTimeOverlap;

public class NetworkEffectiveTimeOverlap extends EffectiveTimeOverlap implements
        NetworkSubsetter {

    public NetworkEffectiveTimeOverlap(Element config)
            throws ConfigurationException {
        super(config);
    }

    public NetworkEffectiveTimeOverlap(TimeRange tr) {
        super(tr);
    }

    public StringTree accept(NetworkAttrImpl network) {
        return new StringTreeLeaf(this, overlaps(network.getEffectiveTime()));
    }

    private static Logger logger = LoggerFactory.getLogger(NetworkEffectiveTimeOverlap.class.getName());
}// NetworkEffectiveTimeOverlap
