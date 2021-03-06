package edu.sc.seis.sod.subsetter.eventChannel.vector;

import org.w3c.dom.Element;

import edu.sc.seis.fissuresUtil.cache.CacheEvent;
import edu.sc.seis.fissuresUtil.hibernate.ChannelGroup;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.subsetter.AbstractScriptSubsetter;
import edu.sc.seis.sod.velocity.event.VelocityEvent;
import edu.sc.seis.sod.velocity.network.VelocityChannelGroup;


public class EventVectorScript extends AbstractScriptSubsetter implements EventVectorSubsetter {

    public EventVectorScript(Element config) {
        super(config);
    }

    @Override
    public StringTree accept(CacheEvent event, ChannelGroup channelGroup, CookieJar cookieJar) throws Exception {
        return runScript(new VelocityEvent(event), new VelocityChannelGroup(channelGroup), cookieJar);
    }

    /** Run the script with the arguments as predefined variables. */
    public StringTree runScript(VelocityEvent event,  VelocityChannelGroup channelGroup, CookieJar cookieJar) throws Exception {
        engine.put("event", event);
        engine.put("channelGroup", channelGroup);
        engine.put("cookieJar", cookieJar);
        return eval();
    }
}
