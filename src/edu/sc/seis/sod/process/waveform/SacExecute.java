package edu.sc.seis.sod.process.waveform;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.xerces.util.DOMUtil;
import org.jacorb.transaction.Sleeper;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import edu.iris.Fissures.IfEvent.EventAccessOperations;
import edu.iris.Fissures.IfNetwork.Channel;
import edu.iris.Fissures.IfSeismogramDC.RequestFilter;
import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.fissuresUtil.database.util.SQLLoader;
import edu.sc.seis.fissuresUtil.exceptionHandler.GlobalExceptionHandler;
import edu.sc.seis.fissuresUtil.gmt.GenericCommandExecute;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.status.StringTreeLeaf;

/**
 * @author crotwell Created on May 20, 2005
 */
public class SacExecute implements WaveformProcess {

    /**
     * @throws Exception
     */
    public SacExecute(Element el) throws Exception {
        NodeList nl = el.getChildNodes();
        for(int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if(n.getNodeName().equals("application")) {
                application = DOMUtil.getChildText(n).trim();
            } else if(n.getNodeName().equals("commands")) {
                commands = DOMUtil.getChildText(n);
            } else if(n.getNodeName().equals("prefix")) {
                prefix = DOMUtil.getChildText(n);
            }
        }
        reader = new PipedReader();
        writer = new PipedWriter(reader);
        stdOutReader = new PipedReader();
        stdout = new PipedWriter(stdOutReader);
        stdErrReader = new PipedReader();
        stderr = new PipedWriter(stdErrReader);
        Thread t = new Thread(new Runnable() {

            public void run() {
                int exitValue;
                try {
                    sacAlive = true;
                    exitValue = GenericCommandExecute.execute(application,
                                                              reader,
                                                              stdout,
                                                              stderr);
                    sacAlive = false;
                    logger.info("SacExecute exit value is: " + exitValue);
                } catch(Throwable e) {
                    GlobalExceptionHandler.handle(e);
                }
            }
        }, "SacExecute");
        t.setDaemon(true);
        t.start();
        expect("SAC>");
        ve = new VelocityEngine();
        Properties props = new Properties();
        ClassLoader cl = SQLLoader.class.getClassLoader();
        props.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                          "org.apache.velocity.runtime.log.SimpleLog4JLogSystem");
        props.setProperty("runtime.log.logsystem.log4j.category",
                          logger.getName());
        ve.init(props);
    }

    /**
     *
     */
    public WaveformResult process(EventAccessOperations event,
                                  Channel channel,
                                  RequestFilter[] original,
                                  RequestFilter[] available,
                                  LocalSeismogramImpl[] seismograms,
                                  CookieJar cookieJar) throws Exception {
        String files = "";
        for(int i = 0; i < seismograms.length; i++) {
            files += " "
                    + (String)cookieJar.get(SaveSeismogramToFile.getCookieName(prefix,
                                                                               channel.get_id(),
                                                                               i));
        } // end of for (int i=0; i<seismograms.length; i++)
        VelocityContext context = new VelocityContext();
        context.put("files", files);
        StringWriter buffer = new StringWriter();
        ve.evaluate(context, buffer, "SacExecute", commands);
        BufferedReader readBuffer = new BufferedReader(new StringReader(buffer.getBuffer()
                .toString()));
        String line;
        while((line = readBuffer.readLine()) != null) {
            send(line.trim());
            expect("SAC>");
        }
        return new WaveformResult(seismograms, new StringTreeLeaf(this, true));
    }

    private void expect(String response) throws IOException {
        System.err.println("Expecting: "+response);
        int index = stdoutBuffer.indexOf(response);
        char[] cbuf = new char[100];
        String buffer;
        PipedReader inReader;
        while(index == -1) {
            System.out.println("Waiting on read...");
            if ( ! sacAlive) {
                throw new IOException("Application "+application+" has exited");
            }
            if (stdOutReader.ready()) {
                int numRead = stdOutReader.read(cbuf);
                stdoutBuffer += new String(cbuf, 0, numRead);
                System.err.println("Does '"+stdoutBuffer+"' match '"+response+"'?");
                index = stdoutBuffer.indexOf(response);
                String prior = stdoutBuffer.substring(0, index+response.length());
                System.out.println("Match: "+prior);
                stdoutBuffer = stdoutBuffer.substring(index+response.length());
                System.out.println("Buffer: "+stdoutBuffer);
            } else if (stdErrReader.ready()) {
                int numRead = stdErrReader.read(cbuf);
                stderrBuffer += new String(cbuf, 0, numRead);
                System.err.println("Does stderr '"+stderrBuffer+"' match '"+response+"'?");
                index = stderrBuffer.indexOf(response);
                String prior = stderrBuffer.substring(0, index+response.length());
                System.out.println("Match: "+prior);
                stderrBuffer = stderrBuffer.substring(index+response.length());
                System.out.println("Buffer: "+stderrBuffer);
            }
            if (index != -1) {
                System.err.println("Yes.");
            } else {
                System.err.println("No.");
            }
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {
            }
        }
    }

    private void send(String cmd) throws IOException {
        System.err.println("Sending: "+cmd);
        writer.write(cmd + "\n");
    }
    
    

    protected void finalize() throws Throwable {
        send("q");
        super.finalize();
    }
    
    protected VelocityEngine ve;

    protected String prefix = "";

    private String application = "sac";

    private String commands;

    private GenericCommandExecute externalApp;
    
    private boolean sacAlive = false;

    PipedReader reader;

    PipedWriter writer;

    PipedWriter stdout, stderr;

    PipedReader stdOutReader;

    PipedReader stdErrReader;
    
    String stdoutBuffer = "";
    
    String stderrBuffer = "";

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(SacExecute.class);
}