package edu.sc.seis.sod.process.waveform;

import java.awt.Dimension;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import edu.iris.Fissures.IfNetwork.ChannelId;
import edu.iris.Fissures.IfSeismogramDC.RequestFilter;
import edu.iris.Fissures.network.ChannelIdUtil;
import edu.iris.Fissures.network.ChannelImpl;
import edu.iris.Fissures.seismogramDC.LocalSeismogramImpl;
import edu.sc.seis.fissuresUtil.cache.CacheEvent;
import edu.sc.seis.fissuresUtil.database.NotFound;
import edu.sc.seis.fissuresUtil.display.RecordSectionDisplay;
import edu.sc.seis.fissuresUtil.display.configuration.DOMHelper;
import edu.sc.seis.fissuresUtil.display.configuration.SeismogramDisplayConfiguration;
import edu.sc.seis.fissuresUtil.display.registrar.CustomLayOutConfig;
import edu.sc.seis.fissuresUtil.xml.DataSet;
import edu.sc.seis.fissuresUtil.xml.DataSetSeismogram;
import edu.sc.seis.fissuresUtil.xml.DataSetToXML;
import edu.sc.seis.fissuresUtil.xml.MemoryDataSetSeismogram;
import edu.sc.seis.fissuresUtil.xml.URLDataSetSeismogram;
import edu.sc.seis.sod.ConfigurationException;
import edu.sc.seis.sod.CookieJar;
import edu.sc.seis.sod.SodUtil;
import edu.sc.seis.sod.Start;
import edu.sc.seis.sod.hibernate.RecordSectionItem;
import edu.sc.seis.sod.hibernate.SodDB;
import edu.sc.seis.sod.status.StringTreeLeaf;
import edu.sc.seis.sod.subsetter.eventChannel.PassEventChannel;

public class RSChannelInfoPopulator implements WaveformProcess {

    public RSChannelInfoPopulator(Element config) throws Exception {
        initConfig(config);
        saveSeisToFile = getSaveSeismogramToFile(saveSeisId);
    }

    public static final String GENS_POPS_XPATH = "//recordSectionDisplayGenerator | //RSChannelInfoPopulator | //externalWaveformProcess[classname/text() = \"edu.sc.seis.rev.map.RecordSectionAndMapGenerator\"]";

    private void initConfig(Element config) throws NoSuchFieldException,
            ConfigurationException {
        orientationId = SodUtil.getText(SodUtil.getElement(config, "orientationId"));
        recordSectionId = SodUtil.getText(SodUtil.getElement(config, "recordSectionId"));
        saveSeisId = DOMHelper.extractText(config, "saveSeisId", orientationId);
        if(DOMHelper.hasElement(config, "embeddedEventChannelProcessor")) {
            channelAcceptor = new EmbeddedEventChannelProcessor(SodUtil.getElement(config,
                                                                                   "embeddedEventChannelProcessor"));
        } else {
            channelAcceptor = new EmbeddedEventChannelProcessor(new PassEventChannel());
        }
        if(DOMHelper.hasElement(config, "percentSeisHeight")) {
            percentSeisHeight = new Double(SodUtil.getText(SodUtil.getElement(config,
                                                                              "percentSeisHeight"))).doubleValue();
        }
        int idealNumberOfSeismograms = 10;
        if(DOMHelper.hasElement(config, "idealNumberOfSeismograms")) {
            String idealNumText = SodUtil.getText(SodUtil.getElement(config,
                                                                     "idealNumberOfSeismograms"));
            idealNumberOfSeismograms = new Integer(idealNumText).intValue();
        }
        int maxNumberOfSeismograms = idealNumberOfSeismograms + 5;
        if(DOMHelper.hasElement(config, "maxNumberOfSeismograms")) {
            String maxSeisText = SodUtil.getText(SodUtil.getElement(config,
                                                                    "maximumSeismogramsPerRecordSection"));
            maxNumberOfSeismograms = new Integer(maxSeisText).intValue();
        }
        if(DOMHelper.hasElement(config, "distanceRange")) {
            distRange = new DistanceRange(SodUtil.getElement(config,
                                                             "distanceRange"));
        }
        if(DOMHelper.hasElement(config, "recordSectionSize")) {
            int width = new Integer(SodUtil.getText(SodUtil.getElement(SodUtil.getElement(config,
                                                                                          "recordSectionSize"),
                                                                       "width"))).intValue();
            int height = new Integer(SodUtil.getText(SodUtil.getElement(SodUtil.getElement(config,
                                                                                           "recordSectionSize"),
                                                                        "height"))).intValue();
            recSecDim = new Dimension(width, height);
        }
        if(distRange != null) {
            spacer = new RecordSectionSpacer(distRange,
                                             idealNumberOfSeismograms,
                                             maxNumberOfSeismograms);
        }
        if(DOMHelper.hasElement(config, "displayConfig")) {
            displayCreator = SeismogramDisplayConfiguration.create(DOMHelper.getElement(config,
                                                                                        "displayConfig"));
        }
    }

    public Dimension getRecSecDimension() {
        return recSecDim;
    }

    public SaveSeismogramToFile getSaveSeismogramToFile() throws Exception {
        return saveSeisToFile;
    }

    public static SaveSeismogramToFile getSaveSeismogramToFile(String saveId)
            throws Exception {
        String xpath = "//saveSeismogramToFile[id/text() = \"" + saveId + "\"]";
        return extractSaveSeis(xpath,
                               "No SaveSeismogramToFile element with id "
                                       + saveId + " found");
    }

    private static SaveSeismogramToFile extractSaveSeis(String xpath,
                                                        String errorMsgIfNotFound)
            throws ConfigurationException {
        Element saveSeisConf = DOMHelper.extractElement(Start.getConfig(),
                                                        xpath);
        if(saveSeisConf == null) {
            throw new ConfigurationException(errorMsgIfNotFound);
        }
        return new SaveSeismogramToFile(saveSeisConf);
    }

    public MemoryDataSetSeismogram[] wrap(DataSetSeismogram[] dss)
            throws Exception {
        MemoryDataSetSeismogram[] memDss = new MemoryDataSetSeismogram[dss.length];
        for(int i = 0; i < memDss.length; i++) {
            memDss[i] = new MemoryDataSetSeismogram(((URLDataSetSeismogram)dss[i]).getSeismograms(),
                                                    dss[i].getDataSet(),
                                                    dss[i].getName(),
                                                    dss[i].getRequestFilter());
        }
        return memDss;
    }

    public WaveformResult process(CacheEvent event,
                                  ChannelImpl chan,
                                  RequestFilter[] original,
                                  RequestFilter[] available,
                                  LocalSeismogramImpl[] seismograms,
                                  CookieJar cookieJar) throws Exception {
        boolean out = updateTable(event,
                                  chan,
                                  original,
                                  available,
                                  seismograms,
                                  cookieJar);
        return new WaveformResult(seismograms, new StringTreeLeaf(this, out));
    }

    public boolean updateTable(CacheEvent event,
                               ChannelImpl channel,
                               RequestFilter[] original,
                               RequestFilter[] available,
                               LocalSeismogramImpl[] seismograms,
                               CookieJar cookieJar) throws Exception {
        if( ! channelAcceptor.process(event,
                                   channel,
                                   original,
                                   available,
                                   seismograms,
                                   cookieJar).isSuccess()) {
            return false;
        }
            DataSetSeismogram[] dss = extractSeismograms(event);
            SodDB soddb = SodDB.getSingleton();
            if(soddb.getRecordSectionItem(orientationId,
                                          recordSectionId, event, channel) != null) {
                return false;
            }
            soddb.put(new RecordSectionItem(orientationId,
                                                           recordSectionId,
                                                           event,
                                                           channel,
                                                           false));
            DataSetSeismogram[] bestSeismos = dss;
            if(spacer != null) {
                bestSeismos = spacer.spaceOut(dss);
            }
            ChannelId[] bestChans = getChannelIds(bestSeismos);
            soddb.updateBestForRecordSection(orientationId,
                                                            recordSectionId,
                                                            event,
                                                            bestChans);
            return true;
    }

    public ChannelId[] getChannelIds(DataSetSeismogram[] dss)
            throws SQLException, NotFound {
        ChannelId[] channelIds = new ChannelId[dss.length];
        for(int j = 0; j < dss.length; j++) {
            channelIds[j] = dss[j].getRequestFilter().channel_id;
        }
        return channelIds;
    }

    public DataSetSeismogram[] extractSeismograms(CacheEvent eao)
            throws Exception {
        DataSet ds = DataSetToXML.load(saveSeisToFile.getDSMLFile(eao)
                .toURI()
                .toURL());
        String[] dataSeisNames = ds.getDataSetSeismogramNames();
        List dss = new ArrayList();
        for(int i = 0; i < dataSeisNames.length; i++) {
            DataSetSeismogram seis = ds.getDataSetSeismogram(dataSeisNames[i]);
            ChannelId chanId = getMatchingChanIdIgnoreDates(seis.getChannelId(),
                                                            ds.getChannelIds());
            if(chanId == null) {
                logger.error("no channel in dataset for id="
                        + ChannelIdUtil.toString(seis.getChannelId())
                        + " even though seismogram is in dataset. Skipping this seismogram.");
                continue;
            }
            ChannelImpl chan = (ChannelImpl)ds.getChannel(chanId);
            if(channelAcceptor.eventChannelSubsetter.accept(eao, chan, null)
                    .isSuccess()) {
                dss.add(seis);
            }
        }
        return (DataSetSeismogram[])dss.toArray(new DataSetSeismogram[0]);
    }

    public RecordSectionDisplay getConfiguredRSDisplay() {
        RecordSectionDisplay rsDisplay = (RecordSectionDisplay)displayCreator.createDisplay();
        if(distRange != null) {
            CustomLayOutConfig custConfig = new CustomLayOutConfig(distRange.getMinDistance(),
                                                                   distRange.getMaxDistance(),
                                                                   percentSeisHeight);
            custConfig.setSwapAxes(rsDisplay.getSwapAxes());
            rsDisplay.setLayout(custConfig);
        }
        return rsDisplay;
    }

    public static ChannelId getMatchingChanIdIgnoreDates(ChannelId chan,
                                                         ChannelId[] channels) {
        for(int i = 0; i < channels.length; i++) {
            if(ChannelIdUtil.areEqualExceptForBeginTime(chan, channels[i])) {
                if(!ChannelIdUtil.areEqual(chan, channels[i])) {
                    logger.debug("seismogram channel "
                            + ChannelIdUtil.toString(chan)
                            + " has a different start time than dataset channel "
                            + ChannelIdUtil.toString(channels[i]));
                }
                return channels[i];
            }
        }
        return null;
    }

    public static ChannelId getMatchingChanIdByStationCode(ChannelId chan,
                                                           ChannelId[] channels) {
        for(int i = 0; i < channels.length; i++) {
            if(channels[i].station_code.equals(chan.station_code)
                    && channels[i].channel_code.equals(chan.channel_code)) {
                if(!ChannelIdUtil.areEqual(chan, channels[i])) {
                    logger.debug("seismogram channel "
                            + ChannelIdUtil.toString(chan)
                            + " is not totally equal to dataset channel "
                            + ChannelIdUtil.toString(channels[i]));
                }
                return channels[i];
            }
        }
        return null;
    }

    public String getOrientationId() {
        return orientationId;
    }

    public String getRecordSectionId() {
        return recordSectionId;
    }

    public String getSaveSeisId() {
        return saveSeisId;
    }

    private SaveSeismogramToFile saveSeisToFile;

    private String orientationId, saveSeisId, recordSectionId;

    private DistanceRange distRange;// = new DistanceRange(0, 180);

    private double percentSeisHeight = 10;

    private Dimension recSecDim = new Dimension(500, 500);

    protected RecordSectionSpacer spacer;

    private SeismogramDisplayConfiguration displayCreator;

    private EmbeddedEventChannelProcessor channelAcceptor;

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(RSChannelInfoPopulator.class);
}