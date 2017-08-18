package edu.sc.seis.sod.process.waveform;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.sc.seis.seisFile.fdsnws.stationxml.Channel;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.hibernate.eventpair.CookieJar;
import edu.sc.seis.sod.model.event.CacheEvent;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.seismogram.RequestFilter;
import edu.sc.seis.sod.subsetter.network.NetworkSubsetter;

/**
 * @author crotwell Created on Mar 18, 2005
 */
public class EmbeddedNetwork implements WaveformProcess {

    public EmbeddedNetwork(NetworkSubsetter networkSubsetter) {
        this.networkSubsetter = networkSubsetter;
    }

    public EmbeddedNetwork(Element config)
            throws ConfigurationException {
        NodeList childNodes = config.getChildNodes();
        Node node;
        for(int counter = 0; counter < childNodes.getLength(); counter++) {
            node = childNodes.item(counter);
            if(node instanceof Element) {
                networkSubsetter = (NetworkSubsetter)SodUtil.load((Element)node,
                                                                            "network");
            }
        }
    }

    public WaveformResult accept(CacheEvent event,
                                  Channel channel,
                                  RequestFilter[] original,
                                  RequestFilter[] available,
                                  LocalSeismogramImpl[] seismograms,
                                  CookieJar cookieJar) throws Exception {
        return new WaveformResult(seismograms,
                                  networkSubsetter.accept(channel.getNetworkAttrImpl()));
    }

    NetworkSubsetter networkSubsetter;
}