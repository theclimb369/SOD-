/**
 * AlwaysSuccess.java
 * 
 * @author Philip Crotwell
 */
package edu.sc.seis.sod.process.waveform.vector;

import org.w3c.dom.Element;
import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfSeismogramDC.RequestFilter;
import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.fissuresUtil.exceptionHandler.GlobalExceptionHandler;
import edu.sc.seis.sod.ChannelGroup;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.status.StringTreeBranch;
import edu.sc.seis.sod.status.StringTreeLeaf;

public class WaveformVectorAlwaysSuccess extends VectorResultWrapper {

    public WaveformVectorAlwaysSuccess(Element config)
            throws ConfigurationException {
        super(config);
    }

    public WaveformVectorResult process(EventAccessOperations event,
                                        ChannelGroup channel,
                                        RequestFilter[][] original,
                                        RequestFilter[][] available,
                                        LocalSeismogramImpl[][] seismograms,
                                        CookieJar cookieJar) {
        try {
            WaveformVectorResult result = subProcess.process(event,
                                                             channel,
                                                             original,
                                                             available,
                                                             seismograms,
                                                             cookieJar);
            return new WaveformVectorResult(result.getSeismograms(),
                                            new StringTreeBranch(this,
                                                                 true,
                                                                 result.getReason()));
        } catch(Exception e) {
            GlobalExceptionHandler.handle("Caught an exception inside Always Success and moving on ...",
                                          e);
            return new WaveformVectorResult(seismograms,
                                            new StringTreeLeaf(this, true));
        }
    }

    public String toString() {
        return "AlwaysSuccess(" + subProcess.toString() + ")";
    }

}