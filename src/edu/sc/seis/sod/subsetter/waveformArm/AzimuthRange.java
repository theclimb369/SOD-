package edu.sc.seis.sod.subsetter.waveformArm;

import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfEvent.Origin;
import edu.iris.Fissures.IfNetwork.Station;
import edu.iris.Fissures.Location;
import edu.sc.seis.TauP.SphericalCoords;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.subsetter.RangeSubsetter;
import org.w3c.dom.Element;


public class AzimuthRange extends RangeSubsetter implements EventStationSubsetter {
    public AzimuthRange (Element config) throws ConfigurationException {
        super(config);
    }

    public boolean accept(EventAccessOperations eventAccess, Station station, CookieJar cookieJar)
        throws Exception {
        float minValue = getMinValue();
        float maxValue = getMaxValue();
        if(minValue > 180) minValue = minValue - 360;
        if(maxValue > 180) maxValue = maxValue - 360;
        Origin origin = eventAccess.get_preferred_origin();
        Location originLoc = origin.my_location;
        Location loc = station.my_location;
        double azimuth = SphericalCoords.azimuth(originLoc.latitude,
                                                 originLoc.longitude,
                                                 loc.latitude,
                                                 loc.longitude);

        if(azimuth >= minValue && azimuth <= maxValue) return true;
        else return false;
    }
}// AzimuthRange
