package edu.sc.seis.sod.subsetter.waveformArm;
import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfNetwork.Channel;
import edu.iris.Fissures.IfNetwork.NetworkAccess;
import edu.iris.Fissures.IfNetwork.Site;
import edu.iris.Fissures.IfNetwork.Station;
import edu.sc.seis.sod.Status;
import edu.sc.seis.sod.status.waveformArm.WaveformStatusProcess;
import org.w3c.dom.Element;


/**
 * WaveformStatusProcess.java
 *
 *
 * Created: Fri Oct 18 14:57:48 2002
 *
 * @author <a href="mailto:">Srinivasa Telukutla</a>
 * @version
 */

public class NullWaveformStatusProcess implements WaveformStatusProcess {
    public NullWaveformStatusProcess (Element config){}

    public NullWaveformStatusProcess (){
    }

    public void begin(EventAccessOperations eventAccess) {
    }

    public void begin(EventAccessOperations eventAccess,
              NetworkAccess networkAccess) {
    }

    public void begin(EventAccessOperations eventAccess,
              Station station) {
    }

    public void begin(EventAccessOperations eventAccess,
              Site site) {
    }

    public void begin(EventAccessOperations eventAccess,
              Channel channel) {
    }

    public void end(EventAccessOperations eventAccess,
            Channel channel,
            Status status,
            String reason) {
    }

    public void end(EventAccessOperations eventAccess,
            Site site) {
    }

    public void end(EventAccessOperations eventAccess,
            Station station) {
    }

    public void end(EventAccessOperations eventAccess,
            NetworkAccess networkAccess) {
    }

    public void end(EventAccessOperations eventAccess) {
    }

    public void closeProcessing() {
    }

}// WaveformStatusProcess
