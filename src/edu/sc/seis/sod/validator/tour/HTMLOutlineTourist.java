/*
 * Created on Jul 14, 2004
 */
package edu.sc.seis.sod.validator.tour;

import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.validator.documenter.SchemaDocumenter;
import edu.sc.seis.sod.validator.model.*;

/**
 * @author Charlie Groves
 */
public class HTMLOutlineTourist implements Tourist {
    public HTMLOutlineTourist(String curLoc) {
        this.curLoc = curLoc;
    }

    public void visit(Attribute attr) {
        genericVisit(attr);
        result.append("An attribute named " + attr.getName()
                + " with a value of ");
    }

    public void leave(Attribute attr) {
        genericLeave(attr);
        result.append("\n");
    }

    public void visit(Choice choice) {
        genericVisit(choice);
        result.append("<i>" + getDefLink(choice) + getChoiceLink()
                + getCardinality(choice) + "</i>\n");
        appendIfChildren = "<div id=\"choice\">\n";
    }

    public void leave(Choice choice) {
        appendIfChildren = "</div><i>end " + getDefLink(choice) + getChoiceLink()
                + "</i>\n<div/>";
        appendIfNoChildren = "<div/>";
        genericLeave(choice);
    }

    private String getChoiceLink() {
        return "<a href=\"" + getTagDocHelpHREF() + "#choice\">choice</a> ";
    }

    public void visit(Data d) {
        genericVisit(d);
        result.append(d.getDatatype());
    }

    public void visit(Empty e) {}

    public void visit(Group g) {
        genericVisit(g);
        result.append("<i>" + getDefLink(g) + getGroupLink()
                + getCardinality(g) + "</i>\n");
        appendIfChildren = "<div id=\"group\">\n";
    }

    public void leave(Group g) {
        appendIfNoChildren = "<div/>";
        appendIfChildren = "</div><i>end " + getDefLink(g) + getGroupLink()
                + "</i>\n";
        genericLeave(g);
    }

    private String getGroupLink() {
        return "<a href=\"" + getTagDocHelpHREF() + "#group\">group</a> ";
    }

    public void visit(Interleave i) {
        genericVisit(i);
        result.append("<i>" + getDefLink(i) + getInterLink()
                + getCardinality(i) + "</i>\n");
        appendIfChildren = "<div id=\"inter\">\n";
    }

    public void leave(Interleave i) {
        appendIfNoChildren = "<div/>";
        appendIfChildren = "</div><i>end " + getDefLink(i) + getInterLink()
                + "</i>\n";
        genericLeave(i);

    }

    private String getInterLink() {
        return "<a href=\"" + getTagDocHelpHREF() + "#interleave\">interleave</a> ";
    }

    public void visit(NamedElement ne) {
        genericVisit(ne);
        String name = "&lt;" + ne.getName() + "&gt;";
        if (ne.getDef() != null) {
            name = getDefLink(ne, name);
        }
        result.append(name);
        if (!isData(ne.getChild())) {
           appendIfChildren = " " + getCardinality(ne) + "<div>\n";
        }
    }

    public void leave(NamedElement ne) {
        appendIfNoChildren = " " + getCardinality(ne) + "<div/>";
        if (!isData(ne.getChild())) {
           appendIfChildren =  "</div>\n&lt;/" + ne.getName() + "&gt;<div/>\n";
        } else {
            appendIfChildren = "&lt;/" + ne.getName() + "&gt; " + getCardinality(ne) + "<div/>\n";
        }
        genericLeave(ne);
    }

    public boolean isData(Form f) {
        return f instanceof Data || f instanceof Value || f instanceof Text;
    }

    public void visit(Text t) {
        genericVisit(t);
        result.append("Any Text");
    }

    public void visit(Value v) {
        genericVisit(v);
        result.append("<div>\"" + v.getValue() + "\"</div>\n");
    }

    public void visit(NotAllowed na) {}

    public String getResult() {
        return result.toString();
    }

    private String getTagDocHelpHREF() {
        String baseString = "../";
        for (int i = 0; i < curLoc.length(); i++) {
            if (curLoc.charAt(i) == '/') {
                baseString += "../";
            }
        }
        baseString += "tagDocHelp.html";
        return baseString;
    }

    private String getDefLink(Form f) {
        if (f.getDef() != null) { return getDefLink(f, f.getDef().getName()); }
        return "";
    }

    private String getDefLink(Form f, String name) {
        String path = SchemaDocumenter.makePath(f.getDef()) + ".html";
        String href = SodUtil.getRelativePath(curLoc, path, "/");
        return "<a href=\"" + href + "\">" + name + "</a>\n";
    }

    private String getCardinality(Form f) {
        String baseString = getTagDocHelpHREF();
        if (f.getMin() == 0) {
            if (f.getMax() == 1) {
                return "<i><a href=\"" + baseString
                        + "#optional\">optional</a></i>";
            } else {
                return "<i><a href=\"" + baseString
                        + "#Any number of times\">Any number of times</a></i>";
            }
        } else {
            if (f.getMax() > 1) {
                return "<i><a href=\"" + baseString
                        + "#At least once\">At least once</a></i>";
            } else {
                return "";
            }
        }
    }
    
    private void genericVisit(Form f) {
        result.append(appendIfChildren);
        appendIfChildren = "";
        appendIfNoChildren = "";
        lastForm = f;
    }
    
    private void genericLeave(Form f) {
        if(f.equals(lastForm)) { result.append(appendIfNoChildren); }
        else { result.append(appendIfChildren); }
        appendIfNoChildren = "";
        appendIfChildren = "";
    }
    
    private Form lastForm;
    
    private String appendIfChildren = "";

    private String appendIfNoChildren = "";
    
    private String curLoc;

    private StringBuffer result = new StringBuffer();
}