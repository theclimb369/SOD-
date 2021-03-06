package edu.sc.seis.sod.tools;

import java.util.Map;

import junit.framework.TestCase;

import com.martiansoftware.jsap.ParseException;

public class PhaseTimeParserTest extends TestCase {

    public void testBasicPhase() throws ParseException {
        Map phase = (Map)ptp.parse("-12.5ttp");
        assertEquals("-12.5", phase.get("offset"));
        assertEquals("ttp", phase.get("name"));
    }

    public void testOriginDefault() throws ParseException {
        Map phase = (Map)ptp.parse("2origin");
        assertEquals("2", phase.get("offset"));
        assertEquals("origin", phase.get("name"));
    }

    public void testPositiveOffset() throws ParseException {
        Map phase = (Map)ptp.parse("+7origin");
        assertEquals("7", phase.get("offset"));
        assertEquals("origin", phase.get("name"));
    }

    public void testNoPhase() throws ParseException {
        try {
            ptp.parse("2006");
            fail("PhaseTimeParsing should fail without a phase given");
        } catch(ParseException pe) {
            assertTrue(pe.getMessage().indexOf("2006") != -1);
        }
    }

    public void testNoOffset() throws ParseException {
        try {
            ptp.parse("ttp");
            fail("PhaseTimeParsing should fail without a offset given");
        } catch(ParseException pe) {
            assertTrue(pe.getMessage().indexOf("ttp") != -1);
        }
    }

    PhaseTimeParser ptp = new PhaseTimeParser();
}
