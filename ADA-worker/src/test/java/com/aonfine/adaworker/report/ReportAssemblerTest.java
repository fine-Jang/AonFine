package com.aonfine.adaworker.report;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.aonfine.adaworker.evidence.EvidenceBundle;

public class ReportAssemblerTest {
    private final ReportAssembler assembler = new ReportAssembler();

    private EvidenceBundle.LeakSuspectEvidence suspect(String id, String title, long bytes, double pct) {
        EvidenceBundle.LeakSuspectEvidence s = new EvidenceBundle.LeakSuspectEvidence();
        s.suspectIdentifier = id; s.title = title; s.retainedBytes = bytes; s.retainedPercent = pct;
        s.significantStackFrames = List.of();
        return s;
    }

    @Test public void fileNameIsDeptTaskDateSanitized() {
        String name = assembler.fileBaseName("법제처/특수", "국가법령*업무");
        assertTrue(name.matches("법제처_특수_국가법령_업무_\\d{8}"));
    }

    @Test public void twoSuspectsFromTwoDifferentDumpsKeepSeparateIdentifiersAndAreNeverSummed() throws Exception {
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.metadata.put("deptNm", "법제처");
        bundle.metadata.put("taskNm", "국가법령");

        EvidenceBundle.DumpEvidence dumpA = new EvidenceBundle.DumpEvidence();
        dumpA.dumpIdentifier = "dumpA.hprof"; dumpA.dumpKind = "HEAP";
        dumpA.leakSuspects = new ArrayList<>();
        dumpA.leakSuspects.add(suspect("dumpA.hprof#Suspect1", "Problem Suspect 1", 2_800_000_000L, 67.96));

        EvidenceBundle.DumpEvidence dumpB = new EvidenceBundle.DumpEvidence();
        dumpB.dumpIdentifier = "dumpB.hprof"; dumpB.dumpKind = "HEAP";
        dumpB.leakSuspects = new ArrayList<>();
        dumpB.leakSuspects.add(suspect("dumpB.hprof#Suspect1", "Problem Suspect 1", 2_280_000_000L, 97.16));

        bundle.dumps.add(dumpA);
        bundle.dumps.add(dumpB);

        File resultDir = Files.createTempDirectory("report-out").toFile();
        File md = assembler.assemble(bundle, "가설: 확인 필요", List.of(), resultDir);
        String content = Files.readString(md.toPath());

        assertTrue(content.contains("dumpA.hprof#Suspect1"));
        assertTrue(content.contains("dumpB.hprof#Suspect1"));
        assertTrue(content.contains("2,800,000,000 bytes"));
        assertTrue(content.contains("2,280,000,000 bytes"));
        // The two dumps' retained bytes must never appear merged into a single combined figure.
        assertFalse(content.contains((2_800_000_000L + 2_280_000_000L) + ""));
    }

    @Test public void missingSymptomIsLabeledNotProvidedRatherThanInvented() throws Exception {
        EvidenceBundle bundle = new EvidenceBundle();
        File resultDir = Files.createTempDirectory("report-out2").toFile();
        File md = assembler.assemble(bundle, "결론", List.of(), resultDir);
        String content = Files.readString(md.toPath());
        assertTrue(content.contains("제공되지 않았습니다"));
    }

    @Test public void copiesRealMatChartImageIntoImagesDirectoryAndLinksIt() throws Exception {
        File chartSrc = File.createTempFile("chart", ".png");
        try (FileOutputStream fos = new FileOutputStream(chartSrc)) { fos.write(new byte[]{(byte) 0x89, 'P', 'N', 'G'}); }

        EvidenceBundle bundle = new EvidenceBundle();
        EvidenceBundle.DumpEvidence dump = new EvidenceBundle.DumpEvidence();
        dump.dumpIdentifier = "dumpA.hprof"; dump.dumpKind = "HEAP";
        dump.leakSuspects = List.of();
        bundle.dumps.add(dump);

        ReportAssembler.ChartImage chart = new ReportAssembler.ChartImage();
        chart.dumpIdentifier = "dumpA.hprof"; chart.sourceFile = chartSrc; chart.caption = "Heap Dump Overview";

        File resultDir = Files.createTempDirectory("report-out3").toFile();
        File md = assembler.assemble(bundle, "결론", List.of(chart), resultDir);
        String content = Files.readString(md.toPath());
        assertTrue(content.contains("images/" + chartSrc.getName()));
        assertTrue(new File(resultDir, "images/" + chartSrc.getName()).isFile());
    }
}
