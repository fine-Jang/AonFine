package com.aonfine.adaworker.mat;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.Test;

/**
 * Fixtures under src/test/resources/mat-fixture/ are a REAL MAT 1.17.0 batch report generated
 * from an actual heap dump captured from AonFine's own intentional OOM test endpoint
 * (com.aonfine.common.diagnostics.HeapExhaustion.trigger), run on the Worker host
 * (192.168.2.17) against its installed MAT+JDK21. These are not synthetic/hand-written HTML --
 * this test locks the parser to the tool's real output shape, not to assumptions about it.
 */
public class MatReportParserTest {
    private static final File FIXTURE_ROOT = new File("src/test/resources/mat-fixture");
    private final MatReportParser parser = new MatReportParser();

    @Test public void parsesRealHeapDumpOverviewNumbers() throws Exception {
        HeapOverview overview = parser.parseHeapOverview(new File(FIXTURE_ROOT, "system_overview/index.html"));
        assertEquals("1.8 GB", overview.get("Used heap dump"));
        assertEquals("1,156,765", overview.get("Number of objects"));
        assertEquals("23,941", overview.get("Number of classes"));
        assertEquals("3,518", overview.get("Number of GC roots"));
        assertEquals("hprof", overview.get("Format"));
        assertEquals(Long.valueOf(1956615454L), overview.fileLengthBytes());
    }

    @Test public void parsesRealLeakSuspectWithExactRetainedBytesAndPercent() throws Exception {
        List<LeakSuspect> suspects = parser.parseLeakSuspects(new File(FIXTURE_ROOT, "leak_suspects/index.html"));
        assertEquals(1, suspects.size());
        LeakSuspect s = suspects.get(0);
        assertEquals("Problem Suspect 1", s.title);
        assertEquals("default task-6", s.threadName);
        assertEquals("0xccdc3c80", s.holderAddress);
        assertEquals(Long.valueOf(1851831728L), s.retainedBytes);
        assertEquals(97.16, s.retainedPercent, 0.001);
        assertEquals("java.lang.Object[]", s.accumulationPointClass);
        assertEquals(Long.valueOf(1851820896L), s.accumulationPointBytes);
        assertEquals(97.16, s.accumulationPointPercent, 0.001);
        assertTrue(s.keywords.contains("com.aonfine.common.diagnostics.HeapExhaustion.trigger()V"));
        assertFalse(s.significantStackFrames.isEmpty());
        assertTrue(s.significantStackFrames.get(0).contains("HeapExhaustion.java:15"));
        assertEquals("pages/24.html", s.stackTraceHref);
        assertEquals("pages/25.html", s.stackTraceWithLocalsHref);
        assertEquals("pages/18.html", s.detailsHref);
    }

    @Test public void followsStackTraceLinkAndReadsRealFrames() throws Exception {
        List<String> lines = parser.parseStackTracePage(new File(FIXTURE_ROOT, "leak_suspects/pages/24.html"));
        assertFalse(lines.isEmpty());
        assertEquals("default task-6", lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.contains("com.aonfine.common.diagnostics.HeapExhaustion.trigger()V (HeapExhaustion.java:15)")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("com.aonfine.common.diagnostics.OomTestController.trigger")));
    }

    @Test public void missingHeadingYieldsEmptyOverviewInsteadOfThrowing() throws Exception {
        File blank = File.createTempFile("blank", ".html");
        blank.deleteOnExit();
        java.nio.file.Files.write(blank.toPath(), "<html><body>no data here</body></html>".getBytes());
        HeapOverview overview = parser.parseHeapOverview(blank);
        assertTrue(overview.fields.isEmpty());
    }
}
