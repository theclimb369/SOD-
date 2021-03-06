package edu.sc.seis.sod.subsetter.station;

import java.util.List;

import org.w3c.dom.Element;

import edu.iris.Fissures.IfNetwork.NetworkAccess;
import edu.iris.Fissures.IfNetwork.NetworkNotFound;
import edu.iris.Fissures.model.MicroSecondDate;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.network.NetworkAttrImpl;
import edu.iris.Fissures.network.StationIdUtil;
import edu.iris.Fissures.network.StationImpl;
import edu.sc.seis.fissuresUtil.cache.CacheNetworkAccess;
import edu.sc.seis.fissuresUtil.chooser.ClockUtil;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.Start;
import edu.sc.seis.sod.UserConfigurationException;
import edu.sc.seis.sod.source.SodSourceException;
import edu.sc.seis.sod.source.network.NetworkSource;
import edu.sc.seis.sod.status.Fail;
import edu.sc.seis.sod.status.Pass;
import edu.sc.seis.sod.status.StringTree;

public class BelongsToVirtual implements StationSubsetter {

    private static NetworkAccess getVirtual(NetworkSource networkSource, String name)
            throws ConfigurationException {
        try {
            List<? extends CacheNetworkAccess> nets = networkSource.getNetworkByName(name);
            if(nets.size() > 1) {
                throw new ConfigurationException("There are several nets with the name "
                        + name);
            }
            return nets.get(0);
        } catch(NetworkNotFound nnf) {
            throw new UserConfigurationException("No network by the name of "
                    + name + " found");
        }
    }

    public BelongsToVirtual(Element el) throws ConfigurationException {
        this(SodUtil.getNestedText(el), null);
    }

    public BelongsToVirtual(String virtualNetName,
                            TimeInterval refreshInterval) {
        this.name = virtualNetName;
        this.refreshInterval = refreshInterval;
    }

    public StringTree accept(StationImpl station, NetworkSource network) throws ConfigurationException, SodSourceException {
        refreshStations(network);
        for (StationImpl sta : stations) {
            if(StationIdUtil.areEqual(station, sta)) {
                return new Pass(this);
            }
        }
        return new Fail(this);
    }

    private void refreshStations(NetworkSource network) throws ConfigurationException, SodSourceException {
        if(ClockUtil.now().subtract(getRefreshInterval()).after(lastQuery)) {
            lastQuery = ClockUtil.now();
            NetworkAccess virtual = getVirtual(network, name);
            stations = network.getStations((NetworkAttrImpl)virtual.get_attributes());
        }
    }
    
    public TimeInterval getRefreshInterval() {
        if (refreshInterval == null) {
            refreshInterval = Start.getNetworkArm().getRefreshInterval();
        }
        return refreshInterval;
    }
    
    private String name;

    private List<? extends StationImpl> stations;

    private TimeInterval refreshInterval;

    private MicroSecondDate lastQuery = new MicroSecondDate(0);

}
