package edu.sc.seis.sod.example;

import edu.iris.Fissures.IfSeismogramDC.RequestFilter;
import edu.iris.Fissures.network.ChannelImpl;
import edu.sc.seis.fissuresUtil.cache.CacheEvent;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.status.Fail;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.subsetter.availableData.AvailableDataSubsetter;


public class AvailableDataSubsetterExample implements AvailableDataSubsetter {

    public StringTree accept(CacheEvent event,
                             ChannelImpl channel,
                             RequestFilter[] original,
                             RequestFilter[] available,
                             CookieJar cookieJar) throws Exception {
        return new Fail(this);
    }
}
