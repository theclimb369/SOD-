package edu.sc.seis.sod.status;


import edu.sc.seis.sod.Start;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class Template{
    /**
     *returns an object of the template type that this class uses, and returns
     * the passed in text when the getResult method of that template type is
     * called
     */
    protected abstract Object textTemplate(final String text);
    
    /**if this class has an template for this tag, it creates it using the
     * passed in element and returns it.  Otherwise it returns null.
     */
    protected Object getTemplate(String tag, Element el){
        if(tag.equals("runName")) return new RunNameTemplate();
        else if(tag.equals("startTime")) return new StartTimeTemplate();
        else if(tag.equals("now"))  return new NowTemplate();
        else if(tag.equals("configFileName")) return textTemplate(Start.getConfigFileName());
        return null;
    }
    
    protected void parse(Element el) {
        parse(el.getChildNodes());
        if(!builtUpText.equals("")) templates.add(textTemplate(builtUpText));
    }
    
    private void parse(NodeList nl) {
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if(n.getNodeType() == Node.TEXT_NODE) addString(n.getNodeValue());
            else{
                String name = n.getNodeName();
                if(name.equals("attribute")) continue;
                Object template = getTemplate(name, (Element)n);
                if(template != null) addTemplate(template);
                else{
                    addString("<" + name);
                    int numAttr = addAttributes(n);
                    if(n.getChildNodes().getLength() - numAttr == 0) addString("/>");
                    else{
                        addString(">");
                        parse(n.getChildNodes());
                        addString("</" + name + ">");
                    }
                }
            }
        }
    }
    
    private void addTemplate(Object template){
        if(!builtUpText.equals("")) templates.add(textTemplate(builtUpText));
        templates.add(template);
        builtUpText = "";
    }
    
    private void addString(String text){  builtUpText += text; }
    
    private String builtUpText = "";
    
    private int addAttributes(Node n) {
        addString(getAttrString(n));
        int numAttr = 0;
        for (int i = 0; i < n.getChildNodes().getLength() && childName(i, n).equals("attribute"); i++) {
            Element attr = (Element)n.getChildNodes().item(i);
            addString(" " + attr.getAttribute("name") + "=\"");
            parse(attr.getChildNodes());
            addString("\"");
            numAttr++;
        }
        return numAttr;
    }
    
    private static String childName(int i, Node n){
        return n.getChildNodes().item(i).getNodeName();
    }
    
    private String getAttrString(Node n){
        String result = "";
        NamedNodeMap attr = n.getAttributes();
        for (int i = 0; i < attr.getLength(); i++) {
            result += " " + attr.item(i).getNodeName();
            result += "=\"" + attr.item(i).getNodeValue() + "\"";
        }
        return result;
    }
    
    public void setUp(){}
    
    protected List templates = new ArrayList();
    
    private static Logger logger = Logger.getLogger(Template.class);
}
