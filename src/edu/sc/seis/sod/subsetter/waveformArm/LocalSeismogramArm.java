package edu.sc.seis.sod.subsetter.waveFormArm;

import edu.sc.seis.sod.*;
import edu.sc.seis.sod.subsetter.*;

import edu.iris.Fissures.IfEvent.*;
import edu.iris.Fissures.event.*;

import edu.iris.Fissures.IfNetwork.*;
import edu.iris.Fissures.network.*;

import edu.iris.Fissures.IfSeismogramDC.*;

import org.w3c.dom.*;
import org.apache.log4j.*;


/**
 * LocalSeismogramArm.java
 *
 *
 * Created: Fri Apr 12 12:48:03 2002
 *
 * @author <a href="mailto:">Srinivasa Telukutla</a>
 * @version
 */

public class LocalSeismogramArm implements Subsetter{
    public LocalSeismogramArm (Element config){
	if ( ! config.getTagName().equals("localSeismogramArm")) {
	    throw new IllegalArgumentException("Configuration element must be a localSeismogramArm tag");
	}
	try {
	    processConfig(config);
	} catch(ConfigurationException ce) {
	    
	    System.out.println("Configuration Exception caught while processing WaveForm Arm");
	}
    }

     /**
     * Describe <code>processConfig</code> method here.
     *
     * @param config an <code>Element</code> value
     * @exception ConfigurationException if an error occurs
     */
    protected void processConfig(Element config) 
	throws ConfigurationException {

	NodeList children = config.getChildNodes();
	Node node;
	for (int i=0; i<children.getLength(); i++) {
	    node = children.item(i);
	    logger.debug(node.getNodeName());
	    if (node instanceof Element) {
		if (((Element)node).getTagName().equals("description")) {
		    // skip description element
		    continue;
		}
		Object sodElement = SodUtil.load((Element)node,"edu.sc.seis.sod.subsetter.waveFormArm");
		if(sodElement instanceof EventChannelSubsetter) eventChannelSubsetter = (EventChannelSubsetter)sodElement;
		else if(sodElement instanceof FixedDataCenter) fixedDataCenterSubsetter = (FixedDataCenter)sodElement;
		else if(sodElement instanceof RequestGenerator) requestGeneratorSubsetter = (RequestGenerator)sodElement;
	
		else if(sodElement instanceof AvailableDataSubsetter) availableDataSubsetter = (AvailableDataSubsetter)sodElement;
		else if(sodElement instanceof LocalSeismogramSubsetter) localSeismogramSubsetter = (LocalSeismogramSubsetter)sodElement;
		else if(sodElement instanceof WaveFormArmProcess) waveFormArmProcessSubsetter = (WaveFormArmProcess)sodElement;
	    } // end of if (node instanceof Element)
	} // end of for (int i=0; i<children.getSize(); i++)

    }

    public void processLocalSeismogramArm(EventAccess eventAccess, NetworkAccess networkAccess, Channel channel) throws Exception{
	
	processEventChannelSubsetter(eventAccess, networkAccess, channel);
	
    }

    /**
     * Describe <code>processEventChannelSubsetter</code> method here.
     *
     * @exception Exception if an error occurs
     */
    public void processEventChannelSubsetter(EventAccess eventAccess, NetworkAccess networkAccess, Channel channel) throws Exception{

	if(eventChannelSubsetter.accept(eventAccess, networkAccess, channel, null)) {
	    processFixedDataCenter(eventAccess, networkAccess, channel);
	}
    }

    /**
     * Describe <code>processFixedDataCenter</code> method here.
     *
     */
    public void processFixedDataCenter(EventAccess eventAccess, NetworkAccess networkAccess, Channel channel) throws Exception{
	DataCenter dataCenter = fixedDataCenterSubsetter.getSeismogramDC();
	if(dataCenter == null) System.out.println("****** Data Center is NULL ******");
	else System.out.println("****** Data Center is NOT NULL ******");
	processRequestGeneratorSubsetter(eventAccess, networkAccess, channel, dataCenter);
	
    }

    /**
     * Describe <code>processRequestGeneratorSubsetter</code> method here.
     *
     */
    public void processRequestGeneratorSubsetter
	(EventAccess eventAccess, NetworkAccess networkAccess, Channel channel, DataCenter dataCenter) throws Exception{
	System.out.println("Processing RequestGenerator");
	RequestFilter[] infilters = requestGeneratorSubsetter.generateRequest(eventAccess, networkAccess, channel, null); 
	System.out.println("==========    GET AVAILABLE DATA ======================== ");
	RequestFilter[] outfilters = dataCenter.available_data(infilters); 
	{
	    processAvailableDataSubsetter(eventAccess, networkAccess, channel, dataCenter, infilters, outfilters);
	}
	
    }
    
    /**
     * Describe <code>processAvailableDataSubsetter</code> method here.
     *
     */
    public void processAvailableDataSubsetter
	(EventAccess eventAccess, NetworkAccess networkAccess, Channel channel, DataCenter dataCenter, RequestFilter[] infilters, RequestFilter[] outfilters) throws Exception{
	if(availableDataSubsetter.accept(eventAccess, networkAccess, channel, infilters, outfilters, null)) {
	    System.out.println("Successfully iterated through the WaveFormArm");
	    System.out.println("============   GET SEISMOGRAMS ===============");
	    System.out.println("The length of outfilters is "+outfilters.length);
	    System.out.println("start time of outfilter is "+outfilters[0].start_time.date_time);
	    System.out.println("start time of outfilter is "+outfilters[0].end_time.date_time);
	    LocalSeismogram[] localSeismograms = dataCenter.retrieve_seismograms(outfilters);
	    processLocalSeismogramSubsetter(eventAccess, networkAccess, channel, infilters, outfilters, localSeismograms);
	}
    }
    
    public void processLocalSeismogramSubsetter
	(EventAccess eventAccess, NetworkAccess networkAccess, Channel channel, RequestFilter[] infilters, RequestFilter[] outfilters, LocalSeismogram[] localSeismograms) throws Exception{ 
	
	if(localSeismogramSubsetter.accept(eventAccess, networkAccess, channel, infilters, outfilters, localSeismograms, null)) {

	    processSeismograms(localSeismograms);

	}
	    
    }
    
    public void processSeismograms(LocalSeismogram[] localSeismograms) {


	System.out.println(" ~~~~~~~~ GOT "+localSeismograms.length+" seismograms");
	
    }

    private EventChannelSubsetter eventChannelSubsetter = new NullEventChannelSubsetter();
    
    private FixedDataCenter fixedDataCenterSubsetter = null;
    
    private RequestGenerator requestGeneratorSubsetter = null;//new NullRequestGenerator();
    
    private AvailableDataSubsetter availableDataSubsetter = new NullAvailableDataSubsetter();

    private LocalSeismogramSubsetter localSeismogramSubsetter = null;

    private WaveFormArmProcess waveFormArmProcessSubsetter;
    
    static Category logger = 
	Category.getInstance(LocalSeismogramArm.class.getName());
    
}// LocalSeismogramArm
