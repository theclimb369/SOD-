package edu.sc.seis.sod.subsetter.waveFormArm;

import edu.sc.seis.sod.*;

import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.sc.seis.sod.subsetter.EventFormatter;
import edu.sc.seis.sod.subsetter.TemplateFileLoader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class WaveformEventTemplateGenerator implements EventStatus, WaveFormStatus{
    public WaveformEventTemplateGenerator(Element el) throws IOException, SAXException, ParserConfigurationException {
		if(Start.getEventArm() != null) Start.getEventArm().add(this);
		NodeList nl = el.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if (n.getNodeName().equals("fileDir")){
				fileDir = n.getFirstChild().getNodeValue();
			}
			else if(n.getNodeName().equals("waveformConfig")){
				config = TemplateFileLoader.getTemplate((Element)n);
				
				Node tmp = SodUtil.getElement(config, "outputLocation");
				formatter = new EventFormatter((Element)tmp);
				config.removeChild(tmp);
				
				tmp = SodUtil.getElement(config, "filename");
				filename = tmp.getFirstChild().getNodeValue();
				config.removeChild(tmp);
			}
		}
		if(formatter == null  || config == null) {
			throw new IllegalArgumentException("The configuration element must contain a fileDir and a waveformConfig");
		}
    }
	
    public void change(EventAccessOperations event, RunStatus status) throws IOException {
		getTemplate(event);
    }
	
    public WaveformEventTemplate getTemplate(EventAccessOperations ev) throws IOException {
		if(!eventTemplates.containsKey(ev)){
			eventTemplates.put(ev, new WaveformEventTemplate(config, fileDir + '/' + formatter.getResult(ev) + '/' + filename, ev));
		}
		return (WaveformEventTemplate)eventTemplates.get(ev);
    }
	
    public boolean contains(EventAccessOperations ev){
		return eventTemplates.containsKey(ev);
    }
	
    public void setArmStatus(String status) {}
	
    public void update(EventChannelPair ecp) {}
	
	
    private Element config;
	
    private EventFormatter formatter;
	
	private String fileDir, filename;
	
    private Map eventTemplates = new HashMap();
}
