package edu.sc.seis.sod.subsetter.request;

import java.util.Iterator;

import org.w3c.dom.Element;

import edu.iris.Fissures.IfSeismogramDC.RequestFilter;
import edu.iris.Fissures.network.ChannelImpl;
import edu.sc.seis.fissuresUtil.cache.CacheEvent;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeBranch;

public final class RequestNOT extends RequestLogical implements RequestSubsetter {

    /**
     * Creates a new <code>RequestSubsetterNOT</code> instance.
     * 
     * @param config
     *            an <code>Element</code> value
     * @exception ConfigurationException
     *                if an error occurs
     */
    public RequestNOT(Element config) throws ConfigurationException {
        super(config);
    }

    public StringTree accept(CacheEvent event,
                          ChannelImpl channel,
                          RequestFilter[] original,
                          CookieJar cookieJar) throws Exception {
        Iterator it = filterList.iterator();
        RequestSubsetter filter = (RequestSubsetter)it.next();
        StringTree result = filter.accept(event, channel, original, cookieJar);
        return new StringTreeBranch(this, ! result.isSuccess(), result);
    }
}