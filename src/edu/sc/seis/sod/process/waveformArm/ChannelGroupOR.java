/**
 * ChannelGroupOR.java
 *
 * @author Created by Omnicore CodeGuide
 */

package edu.sc.seis.sod.process.waveformArm;

import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfSeismogramDC.RequestFilter;
import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.sod.ChannelGroup;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeBranch;
import edu.sc.seis.sod.status.StringTreeLeaf;
import java.util.Iterator;
import java.util.LinkedList;
import org.w3c.dom.Element;

public class ChannelGroupOR extends ChannelGroupFork {

    public ChannelGroupOR(Element config) throws ConfigurationException {
        super(config);
    }

    public ChannelGroupLocalSeismogramResult process(EventAccessOperations event,
                                                     ChannelGroup channelGroup,
                                                     RequestFilter[][] original,
                                                     RequestFilter[][] available,
                                                     LocalSeismogramImpl[][] seismograms,
                                                     CookieJar cookieJar) throws Exception {
        LocalSeismogramImpl[][] out = copySeismograms(seismograms);

        // pass originals to the contained processors
        ChannelGroupLocalSeismogramProcess processor;
        LinkedList reasons = new LinkedList();
        Iterator it = cgProcessList.iterator();
        ChannelGroupLocalSeismogramResult result = new ChannelGroupLocalSeismogramResult(seismograms, new StringTreeLeaf(this, true));
        boolean orResult = false;
        while (it.hasNext()  && ! orResult) {
            processor = (ChannelGroupLocalSeismogramProcess)it.next();
            synchronized (processor) {
                result = processor.process(event,
                                           channelGroup,
                                           original,
                                           available,
                                           copySeismograms(seismograms),
                                           cookieJar);
            }
            orResult |= result.isSuccess();
            reasons.addLast(result.getReason());
        } // end of while (it.hasNext())
        if (reasons.size() < cgProcessList.size()) {
            reasons.addLast(new StringTreeLeaf("ShortCurcit", result.isSuccess()));
        }
        return new ChannelGroupLocalSeismogramResult(out,
                                                     new StringTreeBranch(this,
                                                                          orResult,
                                                                              (StringTree[])reasons.toArray(new StringTree[0])));

    }
}

