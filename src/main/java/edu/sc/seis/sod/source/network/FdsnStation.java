package edu.sc.seis.sod.source.network;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.XMLValidationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.sc.seis.seisFile.SeisFileException;
import edu.sc.seis.seisFile.fdsnws.AbstractFDSNQuerier;
import edu.sc.seis.seisFile.fdsnws.FDSNStationQuerier;
import edu.sc.seis.seisFile.fdsnws.FDSNStationQueryParams;
import edu.sc.seis.seisFile.fdsnws.FDSNWSException;
import edu.sc.seis.seisFile.fdsnws.stationxml.Channel;
import edu.sc.seis.seisFile.fdsnws.stationxml.DataAvailability;
import edu.sc.seis.seisFile.fdsnws.stationxml.FDSNStationXML;
import edu.sc.seis.seisFile.fdsnws.stationxml.NetworkIterator;
import edu.sc.seis.seisFile.fdsnws.stationxml.Response;
import edu.sc.seis.seisFile.fdsnws.stationxml.Station;
import edu.sc.seis.seisFile.fdsnws.stationxml.StationIterator;
import edu.sc.seis.sod.BuildVersion;
import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.Start;
import edu.sc.seis.sod.hibernate.ChannelNotFound;
import edu.sc.seis.sod.model.common.BoxAreaImpl;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.MicroSecondTimeRange;
import edu.sc.seis.sod.model.common.QuantityImpl;
import edu.sc.seis.sod.model.common.TimeInterval;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.model.station.ChannelIdUtil;
import edu.sc.seis.sod.model.station.Instrumentation;
import edu.sc.seis.seisFile.fdsnws.stationxml.InvalidResponse;
import edu.sc.seis.seisFile.fdsnws.stationxml.Network;
import edu.sc.seis.sod.model.station.NetworkIdUtil;
import edu.sc.seis.sod.model.station.StationIdUtil;
import edu.sc.seis.sod.source.SodSourceException;
import edu.sc.seis.sod.source.event.FdsnEvent;
import edu.sc.seis.sod.subsetter.station.StationPointDistance;
import edu.sc.seis.sod.util.convert.stationxml.ChannelSensitivityBundle;
import edu.sc.seis.sod.util.convert.stationxml.StationXMLToFissures;
import edu.sc.seis.sod.util.time.ClockUtil;

public class FdsnStation extends AbstractNetworkSource {

    public FdsnStation() {
        super("defaultFDSNNetwork", -1);
    }
    
    public FdsnStation(String name, int retries, FDSNStationQueryParams queryParams) {
        super(name, retries);
        this.queryParams = queryParams;
    }

    public FdsnStation(Element config) throws Exception {
        super(config);
        queryParams.setIncludeRestricted(false);
        queryParams.setIncludeAvailability(false);
        includeAvailability = SodUtil.isTrue(config, "includeAvailability", true);
        validateXML = SodUtil.isTrue(config, "validate", false);
        if (config != null) {
            // otherwise just use defaults
            int port = SodUtil.loadInt(config, "port", -1);
            if (port > 0) {
                queryParams.setPort(port);
            }
            NodeList childNodes = config.getChildNodes();
            for (int counter = 0; counter < childNodes.getLength(); counter++) {
                Node node = childNodes.item(counter);
                if (node instanceof Element) {
                    Element element = (Element)node;
                    if (element.getTagName().equals("stationBoxArea")) {
                        BoxAreaImpl a = SodUtil.loadBoxArea(element);
                        queryParams.area(a.min_latitude, a.max_latitude, a.min_longitude, a.max_longitude);
                    } else if (element.getTagName().equals("stationPointDistance")) {
                        StationPointDistance pd = (StationPointDistance)SodUtil.load(element, new String[] {"station"});
                        queryParams.donut((float)pd.getLatitude(), (float)pd.getLongitude(), (float)pd.getMin()
                                .getValue(UnitImpl.DEGREE), (float)pd.getMax().getValue(UnitImpl.DEGREE));
                    } else if (element.getTagName().equals("networkCode")) {
                        queryParams.appendToNetwork(SodUtil.getNestedText(element));
                    } else if (element.getTagName().equals("stationCode")) {
                        queryParams.appendToStation(SodUtil.getNestedText(element));
                    } else if (element.getTagName().equals("siteCode")) {
                        queryParams.appendToLocation(SodUtil.getNestedText(element));
                    } else if (element.getTagName().equals("channelCode")) {
                        queryParams.appendToChannel(SodUtil.getNestedText(element));
                    } else if (element.getTagName().equals("includeRestricted")) {
                        queryParams.setIncludeRestricted(true);
                    } else if (element.getTagName().equals("host")) {
                        String host = SodUtil.getNestedText(element);
                        queryParams.setHost(host);
                        this.name = host;
                    } else if (element.getTagName().equals("fdsnwsPath")) {
                        // mainly for beta testing
                        String fdsnwsPath = SodUtil.getNestedText(element);
                        if (fdsnwsPath != null && fdsnwsPath.length() != 0) {
                            queryParams.setFdsnwsPath(fdsnwsPath);
                            logger.debug("Set fdsnwsPath: "+fdsnwsPath);
                        }
                    }
                }
            }
        }
    }
    
    public void includeRestricted(boolean val) {
        queryParams.setIncludeRestricted(val);
    }
    
    @Override
    public List<? extends Network> getNetworks() throws SodSourceException {
        List<Network> out = new ArrayList<Network>();
        FDSNStationXML staxml = null;
        try {
            FDSNStationQueryParams staQP = setupQueryParams();
            staQP.setLevel(FDSNStationQueryParams.LEVEL_NETWORK);
            staQP.clearChannel(); // channel constraints make getting networks very slow
            staQP.clearStartAfter().clearStartBefore().clearStartTime(); // start and end times also slow as 
            staQP.clearEndAfter().clearEndBefore().clearEndTime();       // applied to channel not network
            logger.debug("getNetworks "+staQP.formURI());
            staxml = internalGetStationXML(staQP);
            NetworkIterator netIt = staxml.getNetworks();
            while (netIt.hasNext()) {
                edu.sc.seis.seisFile.fdsnws.stationxml.Network n = netIt.next();
                out.add(n);
            }
            return out;
        } catch(URISyntaxException e) {
            // should not happen
            throw new SodSourceException("Problem forming URI", e);
        } catch(SeisFileException e) {
            throw new SodSourceException(e);
        } catch(XMLValidationException e) {
            logger.warn("InvalidXML: getting networks"+ e.getMessage().replace('\n', ' '));
            // debug to get stack trace in log file, but not in warn which goes to stderr
            logger.debug("InvalidXML: getting networks"+ e.getMessage().replace('\n', ' '), e);
            return out;
        } catch(XMLStreamException e) {
            throw new SodSourceException(e);
        } finally {
            if (staxml != null) {
                staxml.closeReader();
            }
        }
    }

    @Override
    public List<? extends Station> getStations(Network net) throws SodSourceException {
        List<Station> out = new ArrayList<Station>();
        FDSNStationXML staxml = null;
        try {
            FDSNStationQueryParams staQP = setupQueryParams();
            // add any "virtual" network codes back to the query as they limit stations
            // in real networks.
            String netString = staQP.getParam(FDSNStationQueryParams.NETWORK);
            if (netString != null) {
                String[] paramNets = netString.split(",");
                staQP.clearNetwork();
                for (int i = 0; i < paramNets.length; i++) {
                    if (paramNets[i].length() > 2) {
                        // assume virtual, so add to query
                        staQP.appendToNetwork(paramNets[i]);
                    }
                }
            }
            staQP.setLevel(FDSNStationQueryParams.LEVEL_STATION);
            // now append the real network code
            staQP.appendToNetwork(net.getNetworkCode());
            setTimeParams(staQP, net.getStartDateTime(), net.getEndDateTime());
            logger.debug("getStations "+staQP.formURI());
            staxml = internalGetStationXML(staQP);
            NetworkIterator netIt = staxml.getNetworks();
            while (netIt.hasNext()) {
                edu.sc.seis.seisFile.fdsnws.stationxml.Network n = netIt.next();
                StationIterator staIt = n.getStations();
                while (staIt.hasNext()) {
                    edu.sc.seis.seisFile.fdsnws.stationxml.Station s = staIt.next();
                    out.add(s);
                }
            }
            return out;
        } catch(URISyntaxException e) {
            // should not happen
            throw new SodSourceException("Problem forming URI", e);
        } catch(SeisFileException e) {
            throw new SodSourceException(e);
        } catch(XMLValidationException e) {
            // debug to get stack trace in log file, but not in warn which goes to stderr
            logger.warn("InvalidXML: "+NetworkIdUtil.toString(net)+" "+ e.getMessage().replace('\n', ' '));
            logger.debug("InvalidXML: "+NetworkIdUtil.toString(net)+" "+ e.getMessage().replace('\n', ' '), e);
            return out;
        } catch(XMLStreamException e) {
            throw new SodSourceException(e);
        } finally {
            if (staxml != null) {
                staxml.closeReader();
            }
        }
    }

    @Override
    public List<? extends Channel> getChannels(Station station) throws SodSourceException  {
        List<Channel> out = new ArrayList<Channel>();
        FDSNStationXML staxml = null;
        try {
            FDSNStationQueryParams staQP = setupQueryParams();
            staQP.setLevel(FDSNStationQueryParams.LEVEL_CHANNEL);
            staQP.setIncludeAvailability(includeAvailability);
            staQP.clearNetwork()
                    .appendToNetwork(station.getNetworkCode())
                    .clearStation()
                    .appendToStation(station.getStationCode());
            setTimeParams(staQP, station.getStartDateTime(), station.getEndDateTime());
            logger.info("getChannels "+staQP.formURI());
            staxml = internalGetStationXML(staQP);
            NetworkIterator netIt = staxml.getNetworks();
            while (netIt.hasNext()) {
                edu.sc.seis.seisFile.fdsnws.stationxml.Network n = netIt.next();
                Network netAttr = StationXMLToFissures.convert(n);
                StationIterator staIt = n.getStations();
                while (staIt.hasNext()) {
                    edu.sc.seis.seisFile.fdsnws.stationxml.Station s = staIt.next();
                    Station sImpl = StationXMLToFissures.convert(s, netAttr);
                    for (Channel c : s.getChannelList()) {
                        ChannelSensitivityBundle csb = StationXMLToFissures.convert(c, sImpl);
                        Channel outChan = csb.getChan();
                        out.add(outChan);
                        chanSensitivityMap.put(ChannelIdUtil.toString(csb.getChan()), csb.getSensitivity());
                        DataAvailability da = c.getDataAvailability();
                        if (da != null && da.getExtent() != null) {
                            MicroSecondTimeRange range = new MicroSecondTimeRange(new MicroSecondDate(da.getExtent().getStart()),
                                                                                  new MicroSecondDate(da.getExtent().getEnd()));
                            outChan.setAvailabilityExtent(range);
                        }
                    }
                }
            }
            return out;
        } catch(URISyntaxException e) {
            // should not happen
            throw new SodSourceException("Problem forming URI", e);
        } catch(SeisFileException e) {
            throw new SodSourceException(e);
        } catch(XMLValidationException e) {
            // debug to get stack trace in log file, but not in warn which goes to stderr
            logger.warn("InvalidXML: "+StationIdUtil.toString(station)+" "+ e.getMessage().replace('\n', ' '));
            logger.debug("InvalidXML: "+StationIdUtil.toString(station)+" "+ e.getMessage().replace('\n', ' '), e);
            return out;
        } catch(XMLStreamException e) {
            throw new SodSourceException(e);
        } finally {
            if (staxml != null) {
                staxml.closeReader();
            }
        }
    }

    @Override
    public QuantityImpl getSensitivity(Channel chan) throws ChannelNotFound, InvalidResponse, SodSourceException {
        String key = ChannelIdUtil.toString(chan);
        if (!chanSensitivityMap.containsKey(key)) {
            getChannels(chan.getStation());
        }
        if (!chanSensitivityMap.containsKey(key)) {
            throw new ChannelNotFound(chan);
        }
        return chanSensitivityMap.get(key);
    }

    @Override
    public Response getResponse(Channel chan) throws SodSourceException, ChannelNotFound, InvalidResponse  {
        FDSNStationXML staxml = null;
        try {
            if (chan == null) { throw new IllegalArgumentException("Channel is null");}
            FDSNStationQueryParams staQP = setupQueryParams();
            staQP.setLevel(FDSNStationQueryParams.LEVEL_RESPONSE);
            staQP.clearNetwork()
                    .appendToNetwork(chan.getNetworkCode())
                    .clearStation()
                    .appendToStation(chan.getStationCode())
                    .clearLocation()
                    .appendToLocation(chan.getLocCode())
                    .clearChannel()
                    .appendToChannel(chan.getChannelCode());
            setTimeParamsToGetSingleChan(staQP, chan.getStartDateTime(), chan.getEndDateTime());
            logger.debug("getResponse "+staQP.formURI());
            staxml = internalGetStationXML(staQP);
            NetworkIterator netIt = staxml.getNetworks();
            while (netIt.hasNext()) {
                edu.sc.seis.seisFile.fdsnws.stationxml.Network n = netIt.next();
                Network netAttr = StationXMLToFissures.convert(n);
                StationIterator staIt = n.getStations();
                while (staIt.hasNext()) {
                    edu.sc.seis.seisFile.fdsnws.stationxml.Station s = staIt.next();
                    Station sImpl = StationXMLToFissures.convert(s, netAttr);
                    for (Channel c : s.getChannelList()) {
                        ChannelSensitivityBundle csb = StationXMLToFissures.convert(c, sImpl);
                        chanSensitivityMap.put(ChannelIdUtil.toString(csb.getChan()), csb.getSensitivity());
                        // first one should be right 
                        if (staxml != null) {
                            staxml.closeReader();
                            staxml = null;
                        }
                        return c.getResponse();
                    }
                }
            }
            throw new ChannelNotFound(chan);
        } catch(URISyntaxException e) {
            // should not happen
            throw new SodSourceException("Problem forming URI", e);
        } catch(SeisFileException e) {
            throw new SodSourceException(e);
        } catch(XMLValidationException e) {
            // debug to get stack trace in log file, but not in warn which goes to stderr
            logger.warn("InvalidXML: "+ChannelIdUtil.toString(chan)+" "+ e.getMessage().replace('\n', ' '));
            logger.debug("InvalidXML: "+ChannelIdUtil.toString(chan)+" "+ e.getMessage().replace('\n', ' '), e);
            throw new InvalidResponse(e);
        } catch(XMLStreamException e) {
            throw new SodSourceException(e);
        } finally {
            if (staxml != null) {
                staxml.closeReader();
            }
        }
    }

    FDSNStationQueryParams setupQueryParams() {
        FDSNStationQueryParams cloneQP = queryParams.clone();
        if (constraints != null) {
            for (String netCode : constraints.getConstrainingNetworkCodes()) {
                cloneQP.appendToNetwork(netCode);
            }
            for (String staCode : constraints.getConstrainingStationCodes()) {
                cloneQP.appendToStation(staCode);
            }
            for (String siteCode : constraints.getConstrainingLocationCodes()) {
                cloneQP.appendToLocation(siteCode);
            }
            for (String chanCode : constraints.getConstrainingChannelCodes()) {
                cloneQP.appendToChannel(chanCode);
            }
            if (constraints.getConstrainingBeginTime() != null) {
                cloneQP.setEndAfter(constraints.getConstrainingBeginTime());
            }
            if (constraints.getConstrainingEndTime() != null) {
                cloneQP.setStartBefore(constraints.getConstrainingEndTime());
            }
        }
        return cloneQP;
    }
    
    FDSNStationQuerier setupQuerier(FDSNStationQueryParams queryParams) {
        FDSNStationQuerier querier = new FDSNStationQuerier(queryParams);
        if (validateXML) {
            querier.setValidate(true);
        }
        querier.setUserAgent("SOD/"+BuildVersion.getVersion());
        return querier;
    }
    
    FDSNStationXML internalGetStationXML(FDSNStationQueryParams staQP) {
        int count = 0;
        SeisFileException latest = null;
        FDSNStationXML out = null;
        while (count == 0 || getRetryStrategy().shouldRetry(latest, this, count)) {
            try {
                // querier is closed when the FDSNStationXML is closed internal to it
                FDSNStationQuerier querier = setupQuerier(staQP);
                out = querier.getFDSNStationXML();
                if (count > 0) { getRetryStrategy().serverRecovered(this); }
                return out;
            } catch(SeisFileException e) {
                count++;
                if (out != null) {
                    out.closeReader();
                    out = null;
                }
                latest = e;
                Throwable rootCause = AbstractFDSNQuerier.extractRootCause(e);
                if (rootCause instanceof IOException) {
                    // try again on IOException
                } else if (e instanceof FDSNWSException && ((FDSNWSException)e).getHttpResponseCode() != 200) {
                    latest = e;
                    if (((FDSNWSException)e).getHttpResponseCode() == 400) {
                        // badly formed query, cowardly quit
                        Start.simpleArmFailure(Start.getNetworkArm(), FdsnEvent.BAD_PARAM_MESSAGE+" "+((FDSNWSException)e).getMessage()+" on "+((FDSNWSException)e).getTargetURI());
                    }
                } else {
                    throw new RuntimeException(e);
                }
            } catch(OutOfMemoryError e) {
                throw new RuntimeException("Out of memory", e);
            }
        }
        throw new RuntimeException(latest);
    }
    
    public FDSNStationQueryParams getDefaultQueryParams() {
        return queryParams;
    }

    static void setTimeParamsToGetSingleChan(FDSNStationQueryParams staQP, MicroSecondDate startTime, MicroSecondDate endTime) {
        staQP.setStartBefore(new MicroSecondDate(startTime).add(ONE_SECOND));
        MicroSecondDate end = new MicroSecondDate(endTime);
        if (end.before(ClockUtil.now())) {
            staQP.setEndAfter(end.subtract(ONE_SECOND));
        }
    }
    
    static void setTimeParams(FDSNStationQueryParams staQP, MicroSecondDate startTime, MicroSecondDate endTime) {
        staQP.setStartTime(new MicroSecondDate(startTime).add(ONE_SECOND));
        MicroSecondDate end = new MicroSecondDate(endTime).subtract(ONE_SECOND);
        if (end.before(ClockUtil.now())) {
            staQP.setEndTime(end);
        }
    }
    
    boolean includeAvailability = true;
    
    boolean validateXML = false;
    
    public static final TimeInterval ONE_SECOND = new TimeInterval(1, UnitImpl.SECOND);
    
    HashMap<String, QuantityImpl> chanSensitivityMap = new HashMap<String, QuantityImpl>();

    FDSNStationQueryParams queryParams = new FDSNStationQueryParams();
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FdsnStation.class);
}