/**
 * LocalSeismogramResult.java
 *
 * @author Philip Crotwell
 */

package edu.sc.seis.sod.process.waveformArm;

import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;

public class LocalSeismogramResult {

    public LocalSeismogramResult(boolean success, LocalSeismogramImpl[] seismograms) {
        this.success = success;
        this.seismograms = seismograms;
    }

    public boolean isSuccess() {
        return success;
    }

    public LocalSeismogramImpl[] getSeismograms() {
        return seismograms;
    }

    private boolean success;

    private LocalSeismogramImpl[] seismograms;

}

