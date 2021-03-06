/**
 * FissuresFormatterTest.java
 * 
 * @author Philip Crotwell
 */
package edu.sc.seis.sod.status;

import junit.framework.TestCase;
import edu.iris.Fissures.IfNetwork.Channel;
import edu.iris.Fissures.network.StationImpl;
import edu.sc.seis.fissuresUtil.mockFissures.IfNetwork.MockChannel;
import edu.sc.seis.fissuresUtil.mockFissures.IfNetwork.MockStation;
import edu.sc.seis.sod.velocity.network.VelocityStation;

public class FissuresFormatterTest extends TestCase {

    public FissuresFormatterTest(String name) {
        super(name);
    }

    public void setUp() {
        chan = MockChannel.createChannel();
    }

    public void testFormatNetwork() {
        assertEquals("XX70",
                     FissuresFormatter.formatNetwork(chan.get_id().network_id));
    }

    public void testFormatWithDirectories() {
        assertEquals("/2005.265.12/__.BHZ",
                     FissuresFormatter.filize("/2005.265.12/  .BHZ"));
        assertEquals("C:\\home\\_\\__.BHZ",
                     FissuresFormatter.filize("C:\\home\\:\\  .BHZ"));
        assertEquals("_\\__.BHZ", FissuresFormatter.filize(":\\  .BHZ"));
        assertEquals("12442/ham/cheese/__.BHZ",
                     FissuresFormatter.filize("12442/ham/cheese/  .BHZ"));
    }

    public void testOneLineAndClean() {
        StationImpl sta = MockStation.createStation();
        sta.setName("  Long name\nwith\r\nnewlines  ");
        VelocityStation vsta = new VelocityStation(sta);
        assertEquals("Long name with newlines", vsta.getName());
    }
    
    private Channel chan;
}