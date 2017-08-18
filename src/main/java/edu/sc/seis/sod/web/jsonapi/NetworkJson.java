package edu.sc.seis.sod.web.jsonapi;

import org.json.JSONException;
import org.json.JSONWriter;

import edu.sc.seis.seisFile.fdsnws.stationxml.Network;
import edu.sc.seis.sod.model.common.ISOTime;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.station.NetworkIdUtil;
import edu.sc.seis.sod.util.time.ClockUtil;

public class NetworkJson extends AbstractJsonApiData {

    public NetworkJson(Network net, String baseUrl) {
        super(baseUrl);
        this.net = net;
    }

    @Override
    public String getType() {
        return "network";
    }

    @Override
    public String getId() {
        String s = net.get_code();
        if (NetworkIdUtil.isTemporary(net.getId())) {
            s += "_" + net.getBeginTime().getISOString().substring(0, 4); // append
                                                                     // start
                                                                     // year
        }
        return s;
    }

    @Override
    public void encodeAttributes(JSONWriter out) throws JSONException {
        out.key("network-code")
                .value(net.getId().network_code)
                .key("start-time")
                .value(net.getId().begin_time.getISOString())
                .key("end-time")
                .value(encodeEndTime(net.getEndTime()))
                .key("description")
                .value(net.getDescription());
    }

    @Override
    public boolean hasRelationships() {
        return true;
    }

    @Override
    public void encodeRelationships(JSONWriter out) throws JSONException {
        out.key("stations")
                .object()
                .key("links")
                .object()
                .key("self")
                .value(formStationRelationshipURL(net))
                .key("related")
                .value(formStationListURL(net));
        out.endObject(); // end links
        out.endObject(); // end stations
    }

    @Override
    public boolean hasLinks() {
        return true;
    }

    @Override
    public void encodeLinks(JSONWriter out) throws JSONException {
        out.key("self").value(formNetworkURL(net));
    }

    public String formStationRelationshipURL(Network net) {
        String out = baseUrl + "/networks/" + getId() + "/relationships/stations";
        return out;
    }

    public String formNetworkURL(Network net) {
        String out = baseUrl + "/networks/" + getId();
        return out;
    }

    public String formStationListURL(Network net) {
        String out = baseUrl + "/networks/" + getId() + "/stations";
        return out;
    }

    public static Object encodeEndTime(MicroSecondDate endDate) {
        if (endDate.before(ClockUtil.now())) {
            return ISOTime.getISOString(endDate);
        } else {
            return null;
        }
    }

    Network net;
}
