package edu.sc.seis.sod;

/**
 * NetworkFilter.java
 *
 *
 * Created: Thu Dec 13 17:13:46 2001
 *
 * @author <a href="mailto:">Philip Crotwell</a>
 * @version
 */

public interface NetworkFilter {

    public boolean accept(Network network, CookieJar cookies);
    
}// NetworkFilter
