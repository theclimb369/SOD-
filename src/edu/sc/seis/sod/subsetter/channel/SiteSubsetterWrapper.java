package edu.sc.seis.sod.subsetter.channel;

import edu.iris.Fissures.IfNetwork.Channel;
import edu.iris.Fissures.IfNetwork.NetworkAccess;
import edu.sc.seis.sod.subsetter.site.SiteSubsetter;

/**
 * @author groves Created on Mar 6, 2005
 */
public class SiteSubsetterWrapper implements ChannelSubsetter {

    public SiteSubsetterWrapper(SiteSubsetter s) {
        this.s = s;
    }

    public boolean accept(Channel channel, NetworkAccess network) throws Exception {
        return s.accept(channel.my_site, network);
    }

    private SiteSubsetter s;
}