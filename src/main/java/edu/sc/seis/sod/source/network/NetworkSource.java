package edu.sc.seis.sod.source.network;

import java.util.List;

import edu.iris.Fissures.IfNetwork.ChannelNotFound;
import edu.iris.Fissures.IfNetwork.Instrumentation;
import edu.iris.Fissures.IfNetwork.NetworkId;
import edu.iris.Fissures.IfNetwork.NetworkNotFound;
import edu.iris.Fissures.model.QuantityImpl;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.network.ChannelImpl;
import edu.iris.Fissures.network.NetworkAttrImpl;
import edu.iris.Fissures.network.StationImpl;
import edu.sc.seis.fissuresUtil.cache.CacheNetworkAccess;
import edu.sc.seis.fissuresUtil.cache.RetryStrategy;
import edu.sc.seis.fissuresUtil.sac.InvalidResponse;
import edu.sc.seis.sod.source.SodSourceException;
import edu.sc.seis.sod.source.Source;

public interface NetworkSource extends Source {

    public TimeInterval getRefreshInterval();

    public CacheNetworkAccess getNetwork(NetworkAttrImpl attr);

    public List<? extends CacheNetworkAccess> getNetworkByName(String name) throws NetworkNotFound;

    public List<? extends NetworkAttrImpl> getNetworks() throws SodSourceException;

    public List<? extends StationImpl> getStations(NetworkAttrImpl net) throws SodSourceException;

    public List<? extends ChannelImpl> getChannels(StationImpl station) throws SodSourceException;

    public QuantityImpl getSensitivity(ChannelImpl chanId) throws ChannelNotFound, InvalidResponse, SodSourceException;

    public Instrumentation getInstrumentation(ChannelImpl chanId) throws ChannelNotFound, InvalidResponse, SodSourceException;

    public void setConstraints(NetworkQueryConstraints constraints);
    
    public int getRetries();

    public RetryStrategy getRetryStrategy();
}