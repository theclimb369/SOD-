/**
 * ExampleBuilder.java
 *
 * @author Created by Philip Oliver-Paull
 */

package edu.sc.seis.sod.validator.model;

import edu.sc.seis.sod.validator.ModelWalker;
import java.util.ArrayList;
import java.util.List;

public class ExampleBuilder {

    public void write(Form f){
        if (f.getMin() == 0) return;
        if (f instanceof Attribute){
            Attribute attr = (Attribute)f;
            if (!attrQueue.contains(attr)){
                attrQueue.add(attr); //this gets rid of double processing of attributes
                buf.append(' ' + attr.getName() + "=\"");
                Form kid = attr.getChild();
                if (kid != null){
                    write(kid);
                }
                buf.append('\"');
            }
            else {
                attrQueue.remove(attr);
            }
        } else if (f instanceof Choice){
            Choice c = (Choice)f;
            Form[] children = c.getChildren();
            for (int i = 0; i < children.length; i++) {
                if (!ModelWalker.requiresSelfReferentiality(children[i])){
                    write(children[i]);
                    break;
                }
            }
        } else if (f instanceof Value){
            buf.append(DEFAULT_TEXT_VALUE);
        } else if (f instanceof Data){
            buf.append(DEFAULT_INT_VALUE);
        } else if (f instanceof DataList) {
            System.out.println("datalist?");
        } else if (f instanceof Group || f instanceof Interleave) {
            MultigenitorForm m = (MultigenitorForm)f;
            Form[] kids = m.getChildren();
            for (int i = 0; i < kids.length; i++) {
                write(kids[i]);
            }
        } else if (f instanceof NamedElement) {
            NamedElement ne = (NamedElement)f;
            buf.append('<' + ne.getName());
            if (ne.getAttributes() != null){
                Attribute[] attrs = ne.getAttributes();
                for (int i = 0; i < attrs.length; i++) {
                    write(attrs[i]);
                }
            }
            buf.append('>');
            Form kid = ne.getChild();
            if (kid != null){
                write(kid);
            }
            buf.append("</" + ne.getName() + ">\n");
        } else if (f instanceof Text) {
            buf.append(DEFAULT_TEXT_VALUE);
        } else if (f instanceof Empty) {
            System.out.println("empty?");
        }
    }

    public String toString(){
        return buf.toString();
    }

    private StringBuffer buf = new StringBuffer();
    private List attrQueue = new ArrayList();

    public static final String DEFAULT_TEXT_VALUE = "text";
    public static final int DEFAULT_INT_VALUE = 12;
}

