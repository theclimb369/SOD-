/**
 * StationsInNetworkTemplate.java
 *
 * @author Created by Philip Oliver-Paull
 */

package edu.sc.seis.sod.subsetter.networkArm;
import edu.iris.Fissures.IfNetwork.NetworkAccess;
import edu.iris.Fissures.IfNetwork.Station;
import edu.sc.seis.sod.RunStatus;
import edu.sc.seis.sod.subsetter.GenericTemplate;
import edu.sc.seis.sod.subsetter.NetworkFormatter;
import edu.sc.seis.sod.subsetter.StationGroupTemplate;
import edu.sc.seis.sod.subsetter.networkArm.NetworkInfoTemplate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;



public class StationsInNetworkTemplate extends NetworkInfoTemplate{
    
    private NetworkAccess network;
    private List stationListeners = new ArrayList();
    private Logger logger = Logger.getLogger(StationsInNetworkTemplate.class);
    
    public StationsInNetworkTemplate(Element el, String outputLocation, NetworkAccess net){
        super(outputLocation);
        network = net;
        parse(el);
        write();
    }
    
    /**if this class has an template for this tag, it creates it using the
     * passed in element and returns it.  Otherwise it returns null.
     */
    protected Object getTemplate(String tag, Element el) {
        if (tag.equals("stations")){
            StationGroupTemplate sgt = new StationGroupTemplate(el);
            stationListeners.add(sgt);
            return sgt;
        }
        else if (tag.equals("network")){
            return new MyNetworkTemplate(el);
        }
        return super.getTemplate(tag, el);
    }
    
    public void change(Station station, RunStatus status){
        logger.debug("change(station, status): " + station.get_code() + ", " + status.toString());
        Iterator it = stationListeners.iterator();
        while (it.hasNext()){
            ((StationGroupTemplate)it.next()).change(station, status);
        }
        write();
    }
    
    public NetworkAccess getNetwork(){
        return network;
    }
    
    private class MyNetworkTemplate implements GenericTemplate{
        
        public MyNetworkTemplate(Element el){ formatter = new NetworkFormatter(el); }
        
        public String getResult() {
            return formatter.getResult(network);
        }
        
        NetworkFormatter formatter;
    }
    
}

