package edu.sc.seis.sod.subsetter.site;

import org.w3c.dom.Element;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.subsetter.Subsetter;
import edu.sc.seis.sod.subsetter.SubsetterLoader;
import edu.sc.seis.sod.subsetter.station.StationSubsetter;
import edu.sc.seis.sod.subsetter.station.StationSubsetterLoader;

/**
 * @author groves Created on Mar 6, 2005
 */
public class SiteSubsetterLoader implements SubsetterLoader {

    public Subsetter load(Element el) throws ConfigurationException {
        try {
            Object subsetter = SodUtil.load(el, new String[] {"site"});
            return (SiteSubsetter)subsetter;
        } catch(ConfigurationException e) {
            return new StationSubsetterWrapper((StationSubsetter)stationLoader.load(el));
        }
    }

    private StationSubsetterLoader stationLoader = new StationSubsetterLoader();
}