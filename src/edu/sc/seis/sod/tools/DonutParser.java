package edu.sc.seis.sod.tools;

import com.martiansoftware.jsap.FlaggedOption;

public class DonutParser extends PatternParser {

    public DonutParser() {
        super(BoxAreaParser.FOUR_SLASH_DELIMITED_DECIMALS_RE,
              new String[] {"lat", "lon", "min", "max"});
    }

    public static FlaggedOption createParam(String helpMessage) {
        return new FlaggedOption("donut",
                                 new DonutParser(),
                                 null,
                                 false,
                                 'd',
                                 "donut",
                                 helpMessage);
    }

    public String getErrorMessage(String arg) {
        return "A donut is specified as centerLat/centerLon/minRadiusDegrees/maxRadiusDegrees not '"
                + arg + "'";
    }
}
