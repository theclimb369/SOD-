package edu.sc.seis.sod.subsetter;

import edu.sc.seis.sod.*;
import edu.iris.Fissures.*;
import edu.iris.Fissures.model.*;

import org.w3c.dom.*;

/**
 * Describe class <code>Interval</code> here.
 *
 * @author <a href="mailto:">Srinivasa Telukutla</a>
 * @version 1.0
 */
public class Interval implements Subsetter{

    /**
     * Creates a new <code>Interval</code> instance.
     *
     * @param config an <code>Element</code> value
     */
    public Interval(Element config) {
		this.config = config;		
    }

    /**
     * Describe <code>getUnit</code> method here.
     *
     * @return a <code>String</code> value
     */
    public UnitImpl getUnit() throws ConfigurationException{
	return (UnitImpl)SodUtil.load(config,"edu.sc.seis.sod.subsetter");//here the second parameter doesnot matter.
    }

    /**
     * Describe <code>getValue</code> method here.
     *
     * @return a <code>String</code> value
     */
    public double getValue() {
	try {
	    return Double.parseDouble(SodUtil.getNestedText(SodUtil.getElement(config,"value")));
	} catch(Exception e) {
	    return 0.0;
	}
    }

    public QuantityImpl getQuantity() throws ConfigurationException{
	return new QuantityImpl(getValue(), getUnit());
    }
    
    public TimeInterval getTimeInterval() throws ConfigurationException {
	return new TimeInterval(getQuantity());
    }
    
    private Element config;
}//Interval
