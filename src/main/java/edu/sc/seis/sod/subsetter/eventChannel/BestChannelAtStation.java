package edu.sc.seis.sod.subsetter.eventChannel;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import edu.sc.seis.seisFile.fdsnws.stationxml.Channel;
import edu.sc.seis.seisFile.fdsnws.stationxml.Station;
import edu.sc.seis.sod.DOMHelper;
import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.Start;
import edu.sc.seis.sod.bag.BestChannelUtil;
import edu.sc.seis.sod.hibernate.eventpair.CookieJar;
import edu.sc.seis.sod.model.event.CacheEvent;
import edu.sc.seis.sod.model.station.ChannelGroup;
import edu.sc.seis.sod.model.station.ChannelIdUtil;
import edu.sc.seis.sod.status.Fail;
import edu.sc.seis.sod.status.Pass;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.subsetter.eventChannel.vector.EventVectorSubsetter;


public class BestChannelAtStation implements EventChannelSubsetter, EventVectorSubsetter {

    public BestChannelAtStation(Element config) {
        bestChanUtil = new BestChannelUtil();
        if (DOMHelper.hasElement(config, "siteCodeHeuristic")) {
            String siteCodeHeuristic = SodUtil.getNestedText(SodUtil.getElement(config, "siteCodeHeuristic"));
            bestChanUtil.setSiteCodeHeuristic(siteCodeHeuristic.split(","));
        }
        if (DOMHelper.hasElement(config, "gainCodeHeuristic")) {
            String gainCodeHeuristic = SodUtil.getNestedText(SodUtil.getElement(config, "gainCodeHeuristic"));
            bestChanUtil.setGainCodeHeuristic(gainCodeHeuristic.split(","));
        }
        if (DOMHelper.hasElement(config, "bandCodeHeuristic")) {
            String bandCodeHeuristic = SodUtil.getNestedText(SodUtil.getElement(config, "bandCodeHeuristic"));
            bestChanUtil.setBandCodeHeuristic(bandCodeHeuristic.split(","));
        }
        if (DOMHelper.hasElement(config, "orientationCodeHeuristic")) {
            String orientationCodeHeuristic = SodUtil.getNestedText(SodUtil.getElement(config, "orientationCodeHeuristic"));
            bestChanUtil.setOrientationCodeHeuristic(orientationCodeHeuristic.split(","));
        }
        
    }
    
    public StringTree accept(CacheEvent event, Channel channel, CookieJar cookieJar) throws Exception {
        List<Channel> staChans = Start.getNetworkArm().getSuccessfulChannels(((Station)channel.getStation()));
        List<Channel> allChannels = BestChannelUtil.pruneChannels(staChans, event.getOrigin().getTime());
        Channel[] bestChannels = bestChanUtil.getBestMotionVector(allChannels);
        if (bestChannels == null) {
            Channel bestChan = bestChanUtil.getBestChannel(allChannels);
            if (bestChan != null) {
                bestChannels = new Channel[] {bestChan};
            }
        }
        if (bestChannels == null) {
            return new Fail(this, "No best channels");
        }
        for (int i = 0; i < bestChannels.length; i++) {
            if (ChannelIdUtil.areEqual(channel, ((Channel)bestChannels[i]))) {
                return new Pass(this);
            }
        }
        return new Fail(this);
    }

    public StringTree accept(CacheEvent event, ChannelGroup channelGroup, CookieJar cookieJar) throws Exception {
        List<ChannelGroup> staChans = Start.getNetworkArm().getSuccessfulChannelGroups(((Station)channelGroup.getStation()));
        List<Channel> allChannels = new ArrayList<Channel>(staChans.size()*3);
        for (ChannelGroup cg : staChans) {
            Channel[] cgChans = cg.getChannels();
            allChannels.add(cgChans[0]);
            allChannels.add(cgChans[1]);
            allChannels.add(cgChans[2]);
        }
        allChannels = BestChannelUtil.pruneChannels(allChannels, event.getOrigin().getTime());
        Channel[] bestChannels = bestChanUtil.getBestMotionVector(allChannels);
        if (bestChannels == null) {
            return new Fail(this, "No best channel group");
        }
        ChannelGroup best = new ChannelGroup(Channel.implize(bestChannels));
        if (best.areEqual(channelGroup)) {
            return new Pass(this);
        }
        return new Fail(this);
    }
    
    BestChannelUtil bestChanUtil;
}
