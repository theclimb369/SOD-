package edu.sc.seis.sod.process.waveform;

import org.w3c.dom.Element;

import edu.sc.seis.seisFile.fdsnws.stationxml.Channel;
import edu.sc.seis.sod.hibernate.eventpair.CookieJar;
import edu.sc.seis.sod.model.common.MicroSecondTimeRange;
import edu.sc.seis.sod.model.event.CacheEvent;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.seismogram.RequestFilter;
import edu.sc.seis.sod.status.StringTreeLeaf;
import edu.sc.seis.sod.util.time.RangeTool;

/**
 * @author groves Created on Sep 8, 2004
 */
public class SomeDataCoverage implements WaveformProcess {

    public SomeDataCoverage() {}

    public SomeDataCoverage(Element config) {}

    public WaveformResult accept(CacheEvent event,
                                  Channel channel,
                                  RequestFilter[] original,
                                  RequestFilter[] available,
                                  LocalSeismogramImpl[] seismograms,
                                  CookieJar cookieJar) {
        MicroSecondTimeRange[] seisTimeRanges = new MicroSecondTimeRange[seismograms.length];
        for(int i = 0; i < seisTimeRanges.length; i++) {
            seisTimeRanges[i] = new MicroSecondTimeRange(seismograms[i]);
        }
        MicroSecondTimeRange[] rfTimeRanges = new MicroSecondTimeRange[original.length];
        for(int i = 0; i < rfTimeRanges.length; i++) {
            rfTimeRanges[i] = new MicroSecondTimeRange(original[i]);
        }
        for(int i = 0; i < seisTimeRanges.length; i++) {
            MicroSecondTimeRange curSeisTimeRange = seisTimeRanges[i];
            for(int j = 0; j < rfTimeRanges.length; j++) {
                MicroSecondTimeRange rfTimeRange = rfTimeRanges[j];
                if(RangeTool.areOverlapping(curSeisTimeRange, rfTimeRange)) {
                    StringTreeLeaf leaf = new StringTreeLeaf(this,
                                                             true,
                                                             "Some of the data received overlapped the requested time");
                    return new WaveformResult(seismograms, leaf);
                }
            }
        }
        return new WaveformResult(seismograms,
                                  new StringTreeLeaf(this,
                                                     false,
                                                     "No received seismograms matched the original data request"));
    }
}