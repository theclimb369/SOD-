package edu.sc.seis.sod.source.event;

import edu.sc.seis.fissuresUtil.time.MicroSecondTimeRange;

public interface MicroSecondTimeRangeSupplier {

    public MicroSecondTimeRange getMSTR();
}
