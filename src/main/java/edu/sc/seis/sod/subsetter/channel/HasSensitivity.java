package edu.sc.seis.sod.subsetter.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.iris.Fissures.IfNetwork.ChannelNotFound;
import edu.iris.Fissures.model.QuantityImpl;
import edu.iris.Fissures.network.ChannelImpl;
import edu.sc.seis.fissuresUtil.cache.InstrumentationLoader;
import edu.sc.seis.fissuresUtil.sac.InvalidResponse;
import edu.sc.seis.sod.source.SodSourceException;
import edu.sc.seis.sod.source.network.NetworkSource;
import edu.sc.seis.sod.status.Fail;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeLeaf;

public class HasSensitivity implements ChannelSubsetter {

    public StringTree accept(ChannelImpl channel, NetworkSource network) {
        try {
            QuantityImpl sens = network.getSensitivity(channel);
            return new StringTreeLeaf(this, InstrumentationLoader.isValidSensitivity(sens));
        } catch(ChannelNotFound e) {
            return new Fail(this, "No instrumentation");
        } catch (InvalidResponse e) {
            return new Fail(this, "Invalid instrumentation: "+e.getMessage());
        } catch(SodSourceException e) {
            return new Fail(this, "Error getting instrumentation: "+e.getMessage());
        }
    }

    private Logger logger = LoggerFactory.getLogger(HasResponse.class);
}
