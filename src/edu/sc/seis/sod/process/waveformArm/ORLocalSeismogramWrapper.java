/**
 * ORLocalSeismogramSubsetterWrapper.java
 *
 * @author Created by Omnicore CodeGuide
 */

package edu.sc.seis.sod.process.waveformArm;
import edu.sc.seis.sod.subsetter.waveformArm.*;

import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfSeismogramDC.RequestFilter;
import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.sod.ChannelGroup;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.SodUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ORLocalSeismogramWrapper implements ChannelGroupLocalSeismogramProcess {

    public ORLocalSeismogramWrapper(LocalSeismogramProcess subsetter) {
        this.subsetter = subsetter;
    }

    public ORLocalSeismogramWrapper(Element config) throws ConfigurationException{
        NodeList childNodes = config.getChildNodes();
        Node node;
        for(int counter = 0; counter < childNodes.getLength(); counter++) {
            node = childNodes.item(counter);
            if(node instanceof Element) {
                Object obj = SodUtil.load((Element)node, "waveformArm");
                if (obj instanceof LocalSeismogramProcess) {
                    subsetter =
                        (LocalSeismogramProcess) obj;
                } else {
                    throw new ConfigurationException("Object loaded is not an instance of LocalSeismogramProcess: "+obj.getClass().getName());
                }
                break;
            }
        }
    }


    public ChannelGroupLocalSeismogramResult process(EventAccessOperations event,
                                                     ChannelGroup channelGroup,
                                                     RequestFilter[][] original,
                                                     RequestFilter[][] available,
                                                     LocalSeismogramImpl[][] seismograms,
                                                     CookieJar cookieJar) throws Exception {
        LocalSeismogramImpl[][] out = new LocalSeismogramImpl[seismograms.length][];
        boolean b = false;
        for (int i = 0; i < channelGroup.getChannels().length; i++) {
            LocalSeismogramResult result = subsetter.process(event,
                                                             channelGroup.getChannels()[i],
                                                             original[i],
                                                             available[i],
                                                             ForkProcess.copySeismograms(seismograms[i]),
                                                             cookieJar);
            out[i] = result.getSeismograms();
            b |= result.isSuccess();
        }
        if (b) {
            return new ChannelGroupLocalSeismogramResult(true, out);
        }
        return new ChannelGroupLocalSeismogramResult(false, seismograms);
    }

    public String toString() {
        return "ORLocalSeismogramWrapper("+subsetter.toString()+")";
    }

    LocalSeismogramProcess subsetter;
}

