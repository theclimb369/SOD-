/**
 * LocalSeismogramResult.java
 *
 * @author Philip Crotwell
 */

package edu.sc.seis.sod.process.waveformArm;

import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeLeaf;

public class LocalSeismogramResult {

    public LocalSeismogramResult(boolean success, LocalSeismogramImpl[] seismograms) {
        this(success, seismograms, new StringTreeLeaf("", success));
    }

    public LocalSeismogramResult(boolean success, LocalSeismogramImpl[] seismograms, StringTree reason) {
        this.success = success;
        this.seismograms = seismograms;
        this.reason = reason;
    }

    public boolean isSuccess() {
        return success;
    }

    public LocalSeismogramImpl[] getSeismograms() {
        return seismograms;
    }

    public StringTree getReason() {
        return reason;
    }

    private boolean success;

    private LocalSeismogramImpl[] seismograms;

    private StringTree reason;

}

