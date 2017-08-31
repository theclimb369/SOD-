package edu.sc.seis.sod;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import edu.iris.dmc.seedcodec.CodecException;
import edu.sc.seis.seisFile.fdsnws.stationxml.Channel;
import edu.sc.seis.seisFile.mseed.MissingBlockette1000;
import edu.sc.seis.sod.hibernate.SodDB;
import edu.sc.seis.sod.hibernate.eventpair.AbstractEventChannelPair;
import edu.sc.seis.sod.hibernate.eventpair.CookieJar;
import edu.sc.seis.sod.hibernate.eventpair.EventVectorPair;
import edu.sc.seis.sod.model.common.FissuresException;
import edu.sc.seis.sod.model.common.MicroSecondDate;
import edu.sc.seis.sod.model.common.UnitImpl;
import edu.sc.seis.sod.model.event.CacheEvent;
import edu.sc.seis.sod.model.seismogram.LocalSeismogramImpl;
import edu.sc.seis.sod.model.seismogram.RequestFilter;
import edu.sc.seis.sod.model.seismogram.RequestFilterUtil;
import edu.sc.seis.sod.model.station.ChannelGroup;
import edu.sc.seis.sod.model.station.ChannelId;
import edu.sc.seis.sod.model.station.ChannelIdUtil;
import edu.sc.seis.sod.model.status.Stage;
import edu.sc.seis.sod.model.status.Standing;
import edu.sc.seis.sod.model.status.Status;
import edu.sc.seis.sod.process.waveform.WaveformProcess;
import edu.sc.seis.sod.process.waveform.vector.ANDWaveformProcessWrapper;
import edu.sc.seis.sod.process.waveform.vector.WaveformProcessWrapper;
import edu.sc.seis.sod.process.waveform.vector.WaveformVectorAsAvailableData;
import edu.sc.seis.sod.process.waveform.vector.WaveformVectorProcess;
import edu.sc.seis.sod.process.waveform.vector.WaveformVectorProcessWrapper;
import edu.sc.seis.sod.process.waveform.vector.WaveformVectorResult;
import edu.sc.seis.sod.source.seismogram.BatchDataRequest;
import edu.sc.seis.sod.source.seismogram.ConstantSeismogramSourceLocator;
import edu.sc.seis.sod.source.seismogram.PromiseSeismogramList;
import edu.sc.seis.sod.source.seismogram.PromiseSeismogramSource;
import edu.sc.seis.sod.source.seismogram.SeismogramAuthorizationException;
import edu.sc.seis.sod.source.seismogram.SeismogramSource;
import edu.sc.seis.sod.source.seismogram.SeismogramSourceException;
import edu.sc.seis.sod.source.seismogram.SeismogramSourceLocator;
import edu.sc.seis.sod.status.Fail;
import edu.sc.seis.sod.status.Pass;
import edu.sc.seis.sod.status.StringTree;
import edu.sc.seis.sod.status.StringTreeLeaf;
import edu.sc.seis.sod.status.waveformArm.WaveformMonitor;
import edu.sc.seis.sod.subsetter.Subsetter;
import edu.sc.seis.sod.subsetter.availableData.AvailableDataSubsetter;
import edu.sc.seis.sod.subsetter.availableData.vector.ANDAvailableDataWrapper;
import edu.sc.seis.sod.subsetter.availableData.vector.ORAvailableDataWrapper;
import edu.sc.seis.sod.subsetter.availableData.vector.VectorAvailableDataSubsetter;
import edu.sc.seis.sod.subsetter.channel.ChannelEffectiveTimeOverlap;
import edu.sc.seis.sod.subsetter.eventChannel.PassEventChannel;
import edu.sc.seis.sod.subsetter.eventChannel.vector.EventVectorSubsetter;
import edu.sc.seis.sod.subsetter.eventStation.EventStationSubsetter;
import edu.sc.seis.sod.subsetter.request.AtLeastOneRequest;
import edu.sc.seis.sod.subsetter.request.RequestSubsetter;
import edu.sc.seis.sod.subsetter.request.vector.ANDRequestWrapper;
import edu.sc.seis.sod.subsetter.request.vector.VectorRequestSubsetter;
import edu.sc.seis.sod.subsetter.requestGenerator.RequestGenerator;
import edu.sc.seis.sod.subsetter.requestGenerator.vector.RequestGeneratorWrapper;
import edu.sc.seis.sod.subsetter.requestGenerator.vector.VectorRequestGenerator;
import edu.sc.seis.sod.util.exceptionHandler.GlobalExceptionHandler;
import edu.sc.seis.sod.util.time.ClockUtil;
import edu.sc.seis.sod.util.time.ReduceTool;


public class MotionVectorArm extends AbstractWaveformRecipe implements Subsetter {

    public MotionVectorArm(Element config) throws ConfigurationException {
        processConfig(config);
    }

    public void add(WaveformVectorProcess process) {
        processes.add(process);
    }

    public void add(WaveformProcess proc) {
        add(new ANDWaveformProcessWrapper(proc));
    }

    public VectorRequestGenerator getRequestGenerator() {
        return requestGenerator;
    }
    
    public WaveformVectorProcess[] getProcesses() {
        WaveformVectorProcess[] result = new WaveformVectorProcess[processes.size()];
        return (WaveformVectorProcess[])processes.toArray(result);
    }

    public WaveformProcess[] getWaveformProcesses() {
        List waveformProcesses = getWaveformProcesses(getProcesses());
        return (WaveformProcess[])waveformProcesses.toArray(new WaveformProcess[0]);
    }

    public List getWaveformProcesses(WaveformVectorProcess[] procs) {
        List waveformProcesses = new ArrayList();
        for (int i = 0; i < procs.length; i++) {
            if (procs[i] instanceof WaveformVectorProcessWrapper) {
                WaveformVectorProcess[] subProcesses = ((WaveformVectorProcessWrapper)procs[i]).getWrappedProcessors();
                waveformProcesses.addAll(getWaveformProcesses(subProcesses));
            } else if (procs[i] instanceof WaveformProcessWrapper) {
                WaveformProcessWrapper wrapper = (WaveformProcessWrapper)procs[i];
                waveformProcesses.add(wrapper.getWrappedProcess());
            }
        }
        return waveformProcesses;
    }

    public void handle(Element el) throws ConfigurationException {
        Object sodObject = SodUtil.load(el, PACKAGES);
        if (sodObject instanceof EventStationSubsetter) {
            eventStation = (EventStationSubsetter)sodObject;
        } else if (sodObject instanceof WaveformMonitor) {
            addStatusMonitor((WaveformMonitor)sodObject);
        } else if (sodObject instanceof EventVectorSubsetter) {
            eventChannelGroup = (EventVectorSubsetter)sodObject;
        } else if (sodObject instanceof VectorRequestGenerator) {
            requestGenerator = (VectorRequestGenerator)sodObject;
        } else if (sodObject instanceof RequestGenerator) {
            requestGenerator = new RequestGeneratorWrapper((RequestGenerator)sodObject);
        } else if (sodObject instanceof VectorRequestSubsetter) {
            request = (VectorRequestSubsetter)sodObject;
        } else if (sodObject instanceof RequestSubsetter) {
            request = new ANDRequestWrapper((RequestSubsetter)sodObject);
        } else if (sodObject instanceof SeismogramSourceLocator) {
            dcLocator = (SeismogramSourceLocator)sodObject;
            if (dcLocator instanceof ConstantSeismogramSourceLocator) {
                if (Start.getRunProps().getNumWaveformWorkerThreads() == RunProperties.DEFAULT_NUM_WORKER_THREADS) {
                    logger.info("Wrapping "+dcLocator+" to batch requests for speed. Using 3 threads.");
                    Start.getRunProps().setNumWaveformWorkerThreads(3);
                }    
                dcLocator = new BatchDataRequest(dcLocator);
            }
        } else if (sodObject instanceof VectorAvailableDataSubsetter) {
            availData = (VectorAvailableDataSubsetter)sodObject;
        } else if (sodObject instanceof AvailableDataSubsetter) {
            availData = new ANDAvailableDataWrapper((AvailableDataSubsetter)sodObject);
        } else if (sodObject instanceof WaveformVectorProcess) {
            add((WaveformVectorProcess)sodObject);
        } else if (sodObject instanceof WaveformProcess) {
            add((WaveformProcess)sodObject);
        } else {
            throw new ConfigurationException("Unknown tag in MotionVectorArm config. " + el.getLocalName());
        } // end of else
    }
    
    public void processMotionVectorArm(EventVectorPair ecp) {
        StringTree passed;
        CacheEvent eventAccess = ecp.getEvent();
        ChannelGroup channel = ecp.getChannelGroup();
        synchronized(eventChannelGroup) {
            try {
                passed = eventChannelGroup.accept(eventAccess, channel, ecp.getCookieJar());
            } catch(Throwable e) {
                handle(ecp, Stage.EVENT_CHANNEL_SUBSETTER, e);
                return;
            }
        }
        if (passed.isSuccess()) {
            processRequestGeneratorSubsetter(ecp);
        } else {
            ecp.update(Status.get(Stage.EVENT_CHANNEL_SUBSETTER, Standing.REJECT));
            failLogger.info(ecp + ": " + passed.toString());
        }
    }

    public void processRequestGeneratorSubsetter(EventVectorPair ecp) {
        RequestFilter[][] infilters;
        synchronized(requestGenerator) {
            try {
                infilters = requestGenerator.generateRequest(ecp.getEvent(), ecp.getChannelGroup(), ecp.getCookieJar());
                // check to see if at least one request filter exists, otherwise
                // fail
                boolean found = false;
                for (int i = 0; i < infilters.length; i++) {
                    if (infilters[i].length != 0) {
                        found = true;
                    }
                }
                if (!found) {
                    ecp.update(Status.get(Stage.REQUEST_SUBSETTER, Standing.REJECT));
                    failLogger.info("No request generated: "+ecp.toString());
                    return;
                }
            } catch(Throwable e) {
                handle(ecp, Stage.REQUEST_SUBSETTER, e);
                return;
            }
        }
        processRequestSubsetter(ecp, infilters);
    }

    private boolean firstRequest = true;

    public void processRequestSubsetter(EventVectorPair ecp, RequestFilter[][] infilters) {
        StringTree passed;
        for (int i = 0; i < infilters.length; i++) {
            // check channel overlaps request
            RequestFilter coveringRequest = ReduceTool.cover(infilters[i]);
            ChannelEffectiveTimeOverlap chanOverlap = new ChannelEffectiveTimeOverlap(coveringRequest.start_time,
                                                                                      coveringRequest.end_time);
            passed = chanOverlap.accept(ecp.getChannelGroup().getChannels()[i], null); // net source not needed by chanOverlap
            if ( ! passed.isSuccess()) {
                ecp.update(Status.get(Stage.REQUEST_SUBSETTER, Standing.REJECT));
                failLogger.info(ecp.toString()+" channel doesn't overlap request.");
                return;
            }
        }
        synchronized(request) {
            try {
                passed = request.accept(ecp.getEvent(), ecp.getChannelGroup(), infilters, ecp.getCookieJar());
            } catch(Throwable e) {
                handle(ecp, Stage.REQUEST_SUBSETTER, e);
                return;
            }
        }
        if (getProcesses().length == 0 
                && availData.equals(defaultVectorAvailableData)) {
            if (firstRequest) {
                firstRequest = false;
                logger.info("No seismogram data center has been set, so no data is being requested.  If you're only generating BreqFast requests, this is fine.  Otherwise, it's probably an error.");
            }
            return;
        }
        if (passed.isSuccess()) {
            SeismogramSource dataCenter;
            synchronized(dcLocator) {
                try {
                    // ********************************************************
                    // WARNING, the dcLocator only uses the first channel!!! *
                    // ********************************************************
                    dataCenter = dcLocator.getSeismogramSource(ecp.getEvent(),
                                                           ecp.getChannelGroup().getChannels()[0],
                                                           infilters[0],
                                                           ecp.getCookieJar());
                } catch(Throwable e) {
                    handle(ecp, Stage.AVAILABLE_DATA_SUBSETTER, e);
                    return;
                }
            }
            processAvailableDataSubsetter(ecp, dataCenter, infilters);
        } else {
            ecp.update(Status.get(Stage.REQUEST_SUBSETTER, Standing.REJECT));
            failLogger.info(ecp.toString());
        }
    }

    public void processAvailableDataSubsetter(EventVectorPair ecp,
                                              SeismogramSource seismogramSource,
                                              RequestFilter[][] infilters) {
        LinkedList<WaveformVectorProcess> processList = new LinkedList<WaveformVectorProcess>();
        processList.addAll(processes);
        RequestFilter[][] outfilters = null;

        boolean noImplAvailableData = true;
            outfilters = infilters;
            processList.addFirst(new WaveformVectorAsAvailableData(availData));

//TODO eliminate available data step, create availability web service subsetter, but part of request subsetters???
        
        
        StringTree result = new Pass(availData); // init just for noImplAvailableData case
        if (!noImplAvailableData) {
        synchronized(availData) {
            try {
                result = availData.accept(ecp.getEvent(),
                                          ecp.getChannelGroup(),
                                          infilters,
                                          outfilters,
                                          ecp.getCookieJar());
            } catch(Throwable e) {
                handle(ecp, Stage.AVAILABLE_DATA_SUBSETTER, e);
                return;
            }
        }
        }
        if (result.isSuccess()) {
            for (int i = 0; i < infilters.length; i++) {
                for (int j = 0; j < infilters[i].length; j++) {
                    logger.debug("Getting seismograms " + ChannelIdUtil.toString(infilters[i][j].channel_id) + " from "
                            + infilters[i][j].start_time.getISOTime() + " to " + infilters[i][j].end_time.getISOTime());
                } // end of for (int i=0; i<outFilters.length; i++)
            }
            Instant before = ClockUtil.now();
            LocalSeismogramImpl[][] localSeismograms = new LocalSeismogramImpl[ecp.getChannelGroup().getChannels().length][0];
            LocalSeismogramImpl[][] tempLocalSeismograms = new LocalSeismogramImpl[ecp.getChannelGroup().getChannels().length][0];
            // Using infilters as asking for more than is there probably doesn't
            // hurt
            try {
                localSeismograms = getData(ecp, infilters, seismogramSource);
                Instant after = ClockUtil.now();
                logger.info("After getting seismograms, time taken="
                        + after.subtract(before).convertTo(UnitImpl.SECOND)+"  "+localSeismograms[0].length+", "+localSeismograms[1].length+", "+localSeismograms[2].length);
                if (localSeismograms == null) {
                    return;
                }
            } catch(org.omg.CORBA.SystemException e) {
                handle(ecp, Stage.DATA_RETRIEVAL, e, seismogramSource, requestToString(infilters, outfilters));
                return;
            } catch(SeismogramSourceException e) {
                handle(ecp, Stage.DATA_RETRIEVAL, e, seismogramSource, requestToString(infilters, outfilters));
                return;
            }
            for (int i = 0; i < localSeismograms.length; i++) {
                List tempForCast = new ArrayList();
                for (int j = 0; j < localSeismograms[i].length; j++) {
                    if (localSeismograms[i][j] == null) {
                        ecp.update(Status.get(Stage.DATA_RETRIEVAL, Standing.REJECT));
                        logger.error("Got null in seismogram array for channel " + i + " for " + ecp);
                        return;
                    }
                    Channel ecpChan = ecp.getChannelGroup().getChannels()[i];
                    if (!ChannelIdUtil.areEqual(localSeismograms[i][j].channel_id, ChannelId.of(ecpChan))) {
                        // must be server error
                        logger.warn("MV Channel id in returned seismogram doesn not match channelid in request. req="
                                + ChannelIdUtil.toString(ecpChan) + " seis="
                                + ChannelIdUtil.toString(localSeismograms[i][j].channel_id));
                        // fix seis with original id
                        localSeismograms[i][j].channel_id = ChannelId.of(ecpChan);
                    } // end of if ()
                    tempForCast.add(localSeismograms[i][j]);
                } // end of for (int i=0; i<localSeismograms.length; i++)
                tempLocalSeismograms[i] = (LocalSeismogramImpl[])tempForCast.toArray(new LocalSeismogramImpl[0]);
            }
            processSeismograms(ecp, seismogramSource, infilters, outfilters, tempLocalSeismograms, processList);
        } else {
            if(ClockUtil.now().minus(Start.getRunProps().getSeismogramLatency()).isAfter(ecp.getEvent()
                                                                                          .getOrigin()
                                                                                          .getTime())) {
                logger.info("Retry Reject, older than acceptible latency: "+Start.getRunProps().getSeismogramLatency()+" "+ecp);
                ecp.update(Status.get(Stage.AVAILABLE_DATA_SUBSETTER, Standing.REJECT));
            } else if (ecp.getNumRetries() >= SodDB.getSingleton().getMaxRetries()){
                logger.info("Retry Reject, at max retries: "+SodDB.getSingleton().getMaxRetries()+" "+ecp);
                ecp.update(Status.get(Stage.AVAILABLE_DATA_SUBSETTER, Standing.REJECT));
            } else {
                logger.info("Retry Retry, within acceptible latency: "+Start.getRunProps().getSeismogramLatency()+" "+ecp);
                ecp.update(Status.get(Stage.AVAILABLE_DATA_SUBSETTER, Standing.RETRY));
            }
            failLogger.info(ecp + " " + result + " on server " + seismogramSource);
        }
    }

    public void processSeismograms(EventVectorPair ecp,
                                   SeismogramSource seismogramSource,
                                   RequestFilter[][] infilters,
                                   RequestFilter[][] outfilters,
                                   LocalSeismogramImpl[][] localSeismograms,
                                   LinkedList<WaveformVectorProcess> processList) {
        WaveformVectorProcess processor = null;
        WaveformVectorResult result = new WaveformVectorResult(localSeismograms, new StringTreeLeaf(this, true));
        Iterator<WaveformVectorProcess> it = processList.iterator();
        try {
            while (it.hasNext() && result.isSuccess()) {
                processor = it.next();
                result = runProcessorThreadCheck(processor,
                                                 ecp.getEvent(),
                                                 ecp.getChannelGroup(),
                                                 infilters,
                                                 outfilters,
                                                 result.getSeismograms(),
                                                 ecp.getCookieJar());
            } // end of while (it.hasNext())
            logger.debug("finished with " + ChannelIdUtil.toStringNoDates(ecp.getChannelGroup().getChannels()[0]) + " success="
                    + result.isSuccess());
            if (result.isSuccess()) {
                ecp.update(Status.get(Stage.PROCESSOR, Standing.SUCCESS));
            } else {
                ecp.update(Status.get(Stage.PROCESSOR, Standing.REJECT));
                failLogger.info(ecp + " " + result.getReason());
            }
        } catch(Throwable e) {
            MotionVectorArm.handle(ecp, Stage.PROCESSOR, e, seismogramSource, requestToString(infilters, outfilters));
            ecp.update(Status.get(Stage.PROCESSOR, Standing.SYSTEM_FAILURE));
            failLogger.info(ecp + " " + e);
        }
        logger.debug("finished with " + ChannelIdUtil.toStringNoDates(ecp.getChannelGroup().getChannels()[0]));
    }

    public static WaveformVectorResult runProcessorThreadCheck(WaveformVectorProcess processor,
                                                               CacheEvent event,
                                                               ChannelGroup channel,
                                                               RequestFilter[][] original,
                                                               RequestFilter[][] available,
                                                               LocalSeismogramImpl[][] seismograms,
                                                               CookieJar cookieJar) throws Exception {
        if (processor instanceof Threadable && ((Threadable)processor).isThreadSafe()) {
            return internalRunProcessor(processor, event, channel, original, available, seismograms, cookieJar);
        } else {
            synchronized(processor) {
                return internalRunProcessor(processor, event, channel, original, available, seismograms, cookieJar);
            }
        }
    }
    

    
    private static WaveformVectorResult internalRunProcessor(WaveformVectorProcess processor,
                                                       CacheEvent event,
                                                       ChannelGroup channel,
                                                       RequestFilter[][] original,
                                                       RequestFilter[][] available,
                                                       LocalSeismogramImpl[][] seismograms,
                                                       CookieJar cookieJar) throws Exception {
        WaveformVectorResult result;
        try {
            result = processor.accept(event, channel, original, available, seismograms, cookieJar);
        } catch(FissuresException e) {
            if (e.getCause() instanceof CodecException) {
                result = new WaveformVectorResult(seismograms, new Fail(processor, "Unable to decompress data", e));
            } else {
                throw e;
            }
        } catch(CodecException e) {
            result = new WaveformVectorResult(seismograms, new Fail(processor, "Unable to decompress data", e));
        }
        return result;
    }

    private LocalSeismogramImpl[][] getData(EventVectorPair ecp, RequestFilter[][] rf, SeismogramSource seismogramSource)
            throws SeismogramSourceException {
        LocalSeismogramImpl[][] localSeismograms = new LocalSeismogramImpl[rf.length][];
        
        if (seismogramSource instanceof PromiseSeismogramSource) {
            PromiseSeismogramSource promiseSource = (PromiseSeismogramSource)seismogramSource;
            
            List<List<RequestFilter>> in = new ArrayList<List<RequestFilter>>();
            for (int i = 0; i < rf.length; i++) {
                in.add(Arrays.asList(rf[i]));
            }
            List<PromiseSeismogramList> promiseList = promiseSource.promiseRetrieveDataList(in);

            for (int i = 0; i < localSeismograms.length; i++) {
                PromiseSeismogramList pseisList = promiseList.get(i);
                List<LocalSeismogramImpl> s = pseisList.getResult();
                localSeismograms[i] = s.toArray(new LocalSeismogramImpl[0]);
            }
        } else {
        // old style before promises
        for (int i = 0; i < rf.length; i++) {
            if (rf[i].length != 0) {
                
                logger.debug("before retrieve_seismograms");
                localSeismograms[i] = seismogramSource.retrieveData(Arrays.asList(rf[i])).toArray(new LocalSeismogramImpl[0]);
                logger.debug("after successful retrieve_seismograms "+localSeismograms[i].length);
                if (localSeismograms[i].length > 0
                        && !ChannelIdUtil.areEqual(localSeismograms[i][0].channel_id, rf[i][0].channel_id)) {
                    // must be server error
                    logger.warn("MV X Channel id in returned seismogram doesn not match channelid in request. req="
                            + ChannelIdUtil.toString(rf[i][0].channel_id) + " seis="
                            + ChannelIdUtil.toString(localSeismograms[i][0].channel_id));
                }
            } else {
                logger.debug("Failed, retrieve data, no requestFilters for component "+i+" continuing with remaining components.");
                localSeismograms[i] = new LocalSeismogramImpl[0];
            } // end of else
        }
        }
        return localSeismograms;
    }

    private static void handle(EventVectorPair ecp, Stage stage, Throwable t) {
        handle(ecp, stage, t, null, "");
    }

    protected static String requestToString(RequestFilter[][] in, RequestFilter[][] avail) {
        String message = "";
        message += "\n in=" + RequestFilterUtil.toString(in);
        message += "\n avail=" + RequestFilterUtil.toString(avail);
        return message;
    }
    
    protected static void handle(AbstractEventChannelPair ecp, Stage stage, Throwable t, SeismogramSource seismogramSource, String requestString) {
       try {
           if (t instanceof OutOfMemoryError) {
               //can't do much useful, at least get the stack trace before anything else as other
               // code might trigger further OutofMem
               t.printStackTrace(System.err);
               logger.error("", t);
           } else if (t instanceof org.omg.CORBA.SystemException) {
               // don't log exception here, let RetryStragtegy do it
               ecp.update(Status.get(stage, Standing.CORBA_FAILURE));
           } else if (t instanceof SeismogramSourceException) {
               if (t.getCause() != null && t.getCause() instanceof MissingBlockette1000) {
                   
               } else if (t.getCause() != null && t.getCause() instanceof SocketTimeoutException) {
                   // treat just like CORBA SystemException so it is retried later
                   // don't log exception here, let RetryStragtegy do it
                   ecp.update(Status.get(stage, Standing.CORBA_FAILURE));
               }
           } else if (t instanceof SeismogramAuthorizationException) {
               ecp.update(Status.get(stage, Standing.REJECT));
               failLogger.info("Data decompression failure, miniseed without B1000 is not miniseed. "+ ecp +" "+t.getMessage());
           } else {
               ecp.update(t, Status.get(stage, Standing.SYSTEM_FAILURE));
               String message = "";
               if (t instanceof FissuresException) {
                   FissuresException f = (FissuresException)t;
                   message += f.the_error.error_code + " " + f.the_error.error_description ;
               } else if (t instanceof org.omg.CORBA.SystemException) {
                   message = "Network or server problem, SOD will continue to retry this item periodically: ("
                           + t.getClass().getName() + ") " + message;
               }
               try {
                   message += " " + ecp+"\n";
               } catch (LazyInitializationException lazy) {
                   message += "LazyInitializationException after exception, so I can't print the evp\n";
               }
               message += "Source="+seismogramSource+"\n";
               message += "Request="+requestString+"\n";
               if (t instanceof org.omg.CORBA.SystemException) {
                   logger.warn(message, t);
               } else {
                   failLogger.warn(message, t);
               }
           }
       } catch(Throwable tt) {
           GlobalExceptionHandler.handle("Caught " + tt + " while handling " + t, t);
       }
    }

    private EventVectorSubsetter eventChannelGroup = new PassEventChannel();

    private VectorRequestGenerator requestGenerator;

    private VectorRequestSubsetter request = new AtLeastOneRequest();
    
    private static final VectorAvailableDataSubsetter defaultVectorAvailableData = new ORAvailableDataWrapper(defaultAvailableDataSubsetter);
    
    private VectorAvailableDataSubsetter availData = defaultVectorAvailableData;

    private LinkedList<WaveformVectorProcess> processes = new LinkedList<WaveformVectorProcess>();

    private static final Logger logger = LoggerFactory.getLogger(MotionVectorArm.class);

    private static final org.slf4j.Logger failLogger = org.slf4j.LoggerFactory.getLogger("Fail.WaveformVector");
}
