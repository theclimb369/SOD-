package edu.sc.seis.sod.source.network;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.w3c.dom.Element;

import edu.iris.Fissures.IfNetwork.ChannelNotFound;
import edu.iris.Fissures.IfNetwork.Instrumentation;
import edu.iris.Fissures.IfNetwork.NetworkId;
import edu.iris.Fissures.IfNetwork.NetworkNotFound;
import edu.iris.Fissures.model.ISOTime;
import edu.iris.Fissures.model.MicroSecondDate;
import edu.iris.Fissures.model.QuantityImpl;
import edu.iris.Fissures.model.TimeInterval;
import edu.iris.Fissures.model.UnitImpl;
import edu.iris.Fissures.network.ChannelIdUtil;
import edu.iris.Fissures.network.ChannelImpl;
import edu.iris.Fissures.network.InstrumentationImpl;
import edu.iris.Fissures.network.NetworkAttrImpl;
import edu.iris.Fissures.network.NetworkIdUtil;
import edu.iris.Fissures.network.StationIdUtil;
import edu.iris.Fissures.network.StationImpl;
import edu.sc.seis.fissuresUtil.cache.CacheNetworkAccess;
import edu.sc.seis.fissuresUtil.display.configuration.DOMHelper;
import edu.sc.seis.fissuresUtil.sac.InvalidResponse;
import edu.sc.seis.fissuresUtil.stationxml.ChannelSensitivityBundle;
import edu.sc.seis.fissuresUtil.stationxml.StationChannelBundle;
import edu.sc.seis.fissuresUtil.stationxml.StationXMLToFissures;
import edu.sc.seis.fissuresUtil.time.MicroSecondTimeRange;
import edu.sc.seis.fissuresUtil.time.RangeTool;
import edu.sc.seis.seisFile.fdsnws.FDSNStationQueryParams;
import edu.sc.seis.seisFile.fdsnws.stationxml.Channel;
import edu.sc.seis.seisFile.fdsnws.stationxml.FDSNStationXML;
import edu.sc.seis.seisFile.fdsnws.stationxml.Network;
import edu.sc.seis.seisFile.fdsnws.stationxml.NetworkIterator;
import edu.sc.seis.seisFile.fdsnws.stationxml.Station;
import edu.sc.seis.seisFile.fdsnws.stationxml.StationIterator;
import edu.sc.seis.seisFile.fdsnws.stationxml.StationXMLException;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.SodUtil;

@Deprecated
public class StationXML extends AbstractNetworkSource implements NetworkSource {
    
    public StationXML(Element config) throws ConfigurationException {
        super(config);
        if (DOMHelper.hasElement(config, URL_ELEMENT)) {
            url = SodUtil.getNestedText(SodUtil.getElement(config, URL_ELEMENT));
            checkForOldIrisWebService(url);
        }
        if(DOMHelper.hasElement(config, AbstractNetworkSource.REFRESH_ELEMENT)) {
            refreshInterval = SodUtil.loadTimeInterval(SodUtil.getElement(config, AbstractNetworkSource.REFRESH_ELEMENT));
        } else {
            refreshInterval = new TimeInterval(1, UnitImpl.FORTNIGHT);
        }
        parseURL();
    }

    private void checkForOldIrisWebService(String url2) throws ConfigurationException {
        if (url2.startsWith("http://www.iris.edu/ws")) {
            throw new ConfigurationException("This URL appears to point to the deprecated IRIS DMC station web service. "
                    +"This uses the older stationXML schema which is no longer supported by SOD. Please use the new FDSN Station "
                    +"web service with the <fdsnStation> network source.");
        }
    }

    void parseURL() throws ConfigurationException {
        try {
            parsedURL = new URI(url);
            List<String> split = new ArrayList<String>();
            if (parsedURL.getQuery() != null) {
                String[] splitArray = parsedURL.getQuery().split("&");
                for (String s : splitArray) {
                    String[] nvSplit = s.split("=");
                    if (!nvSplit[0].equals("level")) {
                        // zap level as we do that ourselves
                        split.add(s);
                    }
                }
                String newQuery = "";
                boolean first = true;
                for (String s : split) {
                    if (!first) {
                        newQuery += "&";
                    }
                    newQuery += s;
                    first = false;
                }
                parsedURL = new URI(parsedURL.getScheme(),
                                    parsedURL.getUserInfo(),
                                    parsedURL.getHost(),
                                    parsedURL.getPort(),
                                    parsedURL.getPath(),
                                    newQuery,
                                    parsedURL.getFragment());
            }
        } catch(URISyntaxException e) {
            throw new ConfigurationException("Invalid <url> element found.", e);
        }
    }
    
    public String getDNS() {
        return url;
    }

    public String getName() {
        return this.getClass().getName();
    }

    public TimeInterval getRefreshInterval() {
        return refreshInterval;
    }

    public CacheNetworkAccess getNetwork(NetworkAttrImpl attr) {
        return new CacheNetworkAccess(null, attr);
    }

    public List<? extends CacheNetworkAccess> getNetworkByName(String name) throws NetworkNotFound {
        throw new NetworkNotFound();
    }

    public List<? extends NetworkAttrImpl> getNetworks() {
        checkNetsLoaded();
        return Collections.unmodifiableList(networks);
    }

    public List<? extends StationImpl> getStations(NetworkAttrImpl net) {
        checkChansLoaded(NetworkIdUtil.toStringNoDates(net));
        List<StationChannelBundle> bundles = staChanMap.get(NetworkIdUtil.toStringNoDates(net));
        List<StationImpl> out = new ArrayList<StationImpl>();
        for (StationChannelBundle b : bundles) {
            out.add(b.getStation());
        }
        return out;
    }

    public List<? extends ChannelImpl> getChannels(StationImpl station) {
        checkChansLoaded(NetworkIdUtil.toStringNoDates(station.getNetworkAttr()));
        List<StationChannelBundle> bundles = staChanMap.get(NetworkIdUtil.toStringNoDates(station.getNetworkAttr()));
        for (StationChannelBundle b : bundles) {
            if (StationIdUtil.areEqual(station, b.getStation())) {
                List<ChannelImpl> out = new ArrayList<ChannelImpl>();
                for (ChannelSensitivityBundle chanSens : b.getChanList()) {
                    out.add(chanSens.getChan());
                }
                return out;
            }
        }
        return new ArrayList<ChannelImpl>();
    }

    public QuantityImpl getSensitivity(ChannelImpl chan) throws ChannelNotFound, InvalidResponse {
        checkChansLoaded(NetworkIdUtil.toStringNoDates(chan.getId().network_id));
        List<StationChannelBundle> bundles = staChanMap.get(NetworkIdUtil.toStringNoDates(chan.getId().network_id));
        for (StationChannelBundle b : bundles) {
            if (chan.getId().station_code.equals( b.getStation().get_code())) {
                for (ChannelSensitivityBundle chanSens : b.getChanList()) {
                    if (ChannelIdUtil.areEqual(chan.getId(), chanSens.getChan().get_id()) && chanSens.getSensitivity() != null) {
                        return chanSens.getSensitivity();
                    }
                }
            }
        }
        throw new ChannelNotFound();
    }

    public Instrumentation getInstrumentation(ChannelImpl chan) throws ChannelNotFound, InvalidResponse {
        MicroSecondDate chanBegin = new MicroSecondDate(chan.getId().begin_time);
        String newQuery = FDSNStationQueryParams.NETWORK+"="+chan.getId().network_id.network_code+
                "&"+FDSNStationQueryParams.STATION+"="+chan.getId().station_code+
                "&"+FDSNStationQueryParams.LOCATION+"="+chan.getId().site_code+
                "&"+FDSNStationQueryParams.CHANNEL+"="+chan.getId().channel_code+
                "&"+FDSNStationQueryParams.STARTTIME+"="+toDateString(chanBegin)+
                "&"+FDSNStationQueryParams.ENDTIME+"="+toDateString(chanBegin.add(ONE_DAY));
        try {
            URI chanUri = new URI(parsedURL.getScheme(),
                                  parsedURL.getUserInfo(),
                                  parsedURL.getHost(),
                                  parsedURL.getPort(),
                                  parsedURL.getPath(),
                                  newQuery,
                                  parsedURL.getFragment());
            FDSNStationXML sm = retrieveXML(chanUri, "resp");
            NetworkIterator netIt = sm.getNetworks();
            while (netIt.hasNext()) {
                Network n = netIt.next();
                StationIterator staIt = n.getStations();
                while (staIt.hasNext()) {
                    Station s = staIt.next();
                        for (Channel c : s.getChannelList()) {
                                InstrumentationImpl inst = StationXMLToFissures.convertInstrumentation(c);
                                if (RangeTool.areOverlapping(new MicroSecondTimeRange(inst.effective_time),
                                                             new MicroSecondTimeRange(chanBegin.add(ONE_SECOND), chanBegin.add(ONE_DAY)))) {
                                    return inst;
                                }
                                logger.debug("Skipping as wrong start time "+ChannelIdUtil.toString(chan.getId())+" "+inst.effective_time.start_time.date_time+" "+inst.effective_time.end_time.date_time);
                            
                        }
                    
                    
                }
            }
            
        } catch(URISyntaxException e) {
            throw new InvalidResponse("StationXML URL is not valid, should not happen but it did.", e);
        } catch(XMLStreamException e) {
            throw new InvalidResponse("Problem getting response via stationxml.", e);
        } catch(StationXMLException e) {
            throw new InvalidResponse("Problem getting response via stationxml.", e);
        } catch(IOException e) {
            throw new InvalidResponse("Problem getting response via stationxml.", e);
        }
        
        throw new ChannelNotFound();
    }
    
    public void setConstraints(NetworkQueryConstraints constraints) {
        this.constraints = constraints;
    }

    synchronized void checkNetsLoaded() {
        if (networks == null) {
            try {
                parseNets();
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    synchronized void checkChansLoaded(String netCode) {
        if (staChanMap.get(netCode) == null) {
            checkNetsLoaded();
            try {
                parse();
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    static FDSNStationXML retrieveXML(URI u, String level) throws XMLStreamException, StationXMLException, IOException, URISyntaxException  {
        URI chanUri = new URI(u.getScheme(),
                              u.getUserInfo(),
                              u.getHost(),
                              u.getPort(),
                              u.getPath(),
                              u.getQuery()+"&level="+level,
                              u.getFragment());

        logger.info("Retrieve from "+chanUri);
        
        URLConnection urlConn = chanUri.toURL().openConnection();
        if (urlConn instanceof HttpURLConnection) {
            HttpURLConnection conn = (HttpURLConnection)urlConn;
            if (conn.getResponseCode() == 204) {
                // no data
                return retrieveXML(new ByteArrayInputStream(EMPTY_STATIONXML));
            } else if (conn.getResponseCode() != 200) {
                String out = "";
                BufferedReader errReader = null;
                try {
                    errReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    for (String line; (line = errReader.readLine()) != null;) {
                        out += line + "\n";
                    }
                } finally {
                    if (errReader != null) try { 
                        errReader.close(); 
                        conn.disconnect();
                    } catch (IOException e) {
                        throw e;
                    }
                }
                throw new StationXMLException("Error in connection with url: "+chanUri+"  "+out);
            }
        }
        
        return retrieveXML(urlConn.getInputStream());
    }
    
    static FDSNStationXML retrieveXML(InputStream inStream) throws XMLStreamException, StationXMLException, IOException, URISyntaxException {
        InputStream in  = new BufferedInputStream(inStream);

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLEventReader r = factory.createXMLEventReader(in);
        XMLEvent e = r.peek();
        while(! e.isStartElement()) {
            e = r.nextEvent(); // eat this one
            e = r.peek();  // peek at the next
        }
        return new FDSNStationXML(r);
    }
    
    synchronized void parseNets() throws XMLStreamException, StationXMLException, IOException, URISyntaxException {
        networks = new ArrayList<NetworkAttrImpl>();
        logger.info("Parsing networks from "+parsedURL);
        FDSNStationXML stationXML = retrieveXML(parsedURL, FDSNStationQueryParams.LEVEL_NETWORK);
        
        NetworkIterator netIt = stationXML.getNetworks();
        while (netIt.hasNext()) {
            Network net = netIt.next();
            networks.add(StationXMLToFissures.convert(net));
            StationIterator it = net.getStations(); // should be empty, but just make sure
            while(it.hasNext()) {
                Station s = it.next();
            }
        }
        stationXML.closeReader();
        logger.info("found "+networks.size()+" networks after parse");
    }
    
    synchronized void parse() throws XMLStreamException, StationXMLException, IOException, URISyntaxException {
        staChanMap.clear();
        int numChannels = 0;
        logger.info("Parsing channels from "+parsedURL);
        FDSNStationXML stationXML = retrieveXML(parsedURL, FDSNStationQueryParams.LEVEL_CHANNEL);
        lastLoadDate = stationXML.getCreated();
        NetworkIterator netIt = stationXML.getNetworks();
        while (netIt.hasNext()) {
            Network net = netIt.next();
            String key = NetworkIdUtil.toStringNoDates(StationXMLToFissures.convert(net).getId());
            if ( ! staChanMap.containsKey(key)) {
                staChanMap.put(key, new ArrayList<StationChannelBundle>());
            }
            StationIterator it = net.getStations();
            while(it.hasNext()) {
                Station s = it.next();
                try {
                    numChannels += processStation(networks, s);
                } catch (StationXMLException ee) {
                    logger.error("Skipping "+s.getNetworkCode()+"."+s.getCode()+" "+ ee.getMessage());
                }
            }
        }
        logger.info("found "+ numChannels+" channels in "+networks.size()+" networks after parse ");
    }
    
    int processStation(List<NetworkAttrImpl> netList, Station s) throws StationXMLException {
        int numChannels = 0;
        for (String ignore : ignoreNets) {
            if (s.getNetworkCode().equals(ignore)) {
            // not sure what AB network is, skip it for now
            return 0;
            }
        }
        List<StationChannelBundle> bundles = StationXMLToFissures.convert(s, netList, true);
        for (StationChannelBundle b : bundles) {
            String staKey = NetworkIdUtil.toStringNoDates(b.getStation().getNetworkAttr());
            if ( ! staChanMap.containsKey(staKey)) {
                staChanMap.put(staKey, new ArrayList<StationChannelBundle>());
            }
            staChanMap.get(staKey).add(b);
            numChannels += b.getChanList().size();
        }
        return numChannels;
    }
    
    public static String toDateString(MicroSecondDate msd) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        df.setTimeZone(ISOTime.UTC);
        return df.format(msd);
    }
    
    NetworkQueryConstraints constraints;
    
    static String[] ignoreNets = new String[] {"AB", "AI", "BN"};
    
    List<NetworkAttrImpl> knownNetworks = new ArrayList<NetworkAttrImpl>();;
    
    List<NetworkAttrImpl> networks;
    
    Map<String, List<StationChannelBundle>> staChanMap = new HashMap<String, List<StationChannelBundle>>();
    
    String url;
    
    URI parsedURL;
    
    TimeInterval refreshInterval;
    
    String lastLoadDate;

    public static final TimeInterval ONE_SECOND = new TimeInterval(1, UnitImpl.SECOND);
    
    public static final TimeInterval ONE_DAY = new TimeInterval(1, UnitImpl.DAY);
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StationXML.class);
    
    public static final String URL_ELEMENT = "url";
    
    public static final byte[] EMPTY_STATIONXML = "<StaMessage xsi:schemaLocation=\"http://www.data.scec.org/xml/station/20120307/ http://www.data.scec.org/xml/station/20120307/station.xsd http://www.iris.edu/ws/schemas/station/2012/03/22/ http://www.iris.edu/ws/schemas/station/2012/03/22/station_comments.xsd\"/>".getBytes();
}
