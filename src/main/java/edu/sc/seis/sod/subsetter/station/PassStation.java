package edu.sc.seis.sod.subsetter.station;

import edu.sc.seis.seisFile.fdsnws.stationxml.Station;
import edu.sc.seis.sod.source.network.NetworkSource;
import edu.sc.seis.sod.status.Pass;
import edu.sc.seis.sod.status.StringTree;

/**
 * PassStation.java
 *
 *
 * Created: Thu Dec 13 17:09:18 2001
 *
 * @author Philip Crotwell
 */

public class  PassStation implements StationSubsetter{

    public StringTree accept(Station station, NetworkSource network) { return new Pass(this);  }

}// PassStation
