package com.aonfine.adaworker.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.aonfine.adaworker.evidence.EvidenceBundle;

/**
 * Builds the final Markdown report. Every number/table here comes straight from EvidenceBundle
 * (i.e. from MAT/thread-dump/log parsing) via simple string formatting -- Claude's drafted text
 * is only ever appended as the "결론 및 권고" (interpretation) section, never used as the source
 * of a figure. This keeps the split the project requires: Worker/MAT produce facts, Claude only
 * interprets them.
 */
public final class ReportAssembler {

    public static final class ChartImage {
        public String dumpIdentifier;
        public File sourceFile;   // the PNG MAT itself generated
        public String caption;
    }

    public String fileBaseName(String deptNm, String taskNm) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return sanitize(deptNm) + "_" + sanitize(taskNm) + "_" + date;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "미상";
        String cleaned = value.trim().replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        return cleaned.isEmpty() ? "미상" : cleaned;
    }

    /** @param resultDir will contain the .md file plus images/ and evidence/ subfolders. */
    public File assemble(EvidenceBundle bundle, String claudeConclusionMarkdown, List<ChartImage> charts, File resultDir) throws IOException {
        File imagesDir = new File(resultDir, "images");
        File evidenceDir = new File(resultDir, "evidence");
        Files.createDirectories(imagesDir.toPath());
        Files.createDirectories(evidenceDir.toPath());

        StringBuilder md = new StringBuilder();
        appendHeader(md, bundle);
        appendSymptom(md, bundle);
        appendDumpSections(md, bundle, charts, imagesDir);
        appendConclusion(md, claudeConclusionMarkdown);
        appendEvidenceReferences(md, bundle, evidenceDir);

        String baseName = fileBaseName(bundle.metadata.get("deptNm"), bundle.metadata.get("taskNm"));
        File mdFile = new File(resultDir, baseName + ".md");
        Files.writeString(mdFile.toPath(), md.toString(), StandardCharsets.UTF_8);
        return mdFile;
    }

    private void appendHeader(StringBuilder md, EvidenceBundle bundle) {
        md.append("# 덤프 분석 보고서\n\n");
        md.append("## 1. 기본정보\n\n");
        md.append("| 항목 | 값 |\n|---|---|\n");
        appendRow(md, "부처명", bundle.metadata.get("deptNm"));
        appendRow(md, "업무명", bundle.metadata.get("taskNm"));
        appendRow(md, "호스트명", bundle.metadata.get("hostNm"));
        appendRow(md, "요청자", bundle.metadata.get("requestedBy"));
        appendRow(md, "원본 파일", bundle.metadata.get("originalFileNm"));
        appendRow(md, "덤프 ID", bundle.metadata.get("dumpId"));
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        appendRow(md, "보고서 생성일", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withLocale(Locale.KOREA)) + " KST");
        md.append('\n');
    }

    private void appendRow(StringBuilder md, String key, String value) {
        md.append("| ").append(key).append(" | ").append(value == null || value.isBlank() ? "미제공" : value).append(" |\n");
    }

    private void appendSymptom(StringBuilder md, EvidenceBundle bundle) {
        md.append("## 2. 증상\n\n");
        String symptom = bundle.metadata.get("symptomDescription");
        if (symptom == null || symptom.isBlank()) {
            md.append("사용자가 입력한 증상 설명이 제공되지 않았습니다. (업로드 시 별도로 수집되지 않음)\n\n");
        } else {
            md.append(symptom).append("\n\n");
        }
    }

    private void appendDumpSections(StringBuilder md, EvidenceBundle bundle, List<ChartImage> charts, File imagesDir) throws IOException {
        md.append("## 3. 덤프별 분석\n\n");
        for (EvidenceBundle.DumpEvidence dump : bundle.dumps) {
            md.append("### 3.").append(bundle.dumps.indexOf(dump) + 1).append(". ").append(dump.dumpIdentifier)
                    .append(" (").append(dump.dumpKind).append(")\n\n");
            if (dump.note != null) {
                md.append("> ").append(dump.note).append("\n\n");
            }
            if ("HEAP".equals(dump.dumpKind)) {
                appendHeapOverview(md, dump);
                appendChartsFor(md, charts, dump.dumpIdentifier, imagesDir);
                appendLeakSuspects(md, dump);
            } else if ("THREAD".equals(dump.dumpKind)) {
                appendThreadSection(md, dump);
            }
        }
    }

    private void appendHeapOverview(StringBuilder md, EvidenceBundle.DumpEvidence dump) {
        if (dump.heapOverview == null || dump.heapOverview.isEmpty()) return;
        md.append("#### Heap Dump Overview\n\n| 항목 | 값 |\n|---|---|\n");
        for (Map.Entry<String, String> e : dump.heapOverview.entrySet()) {
            md.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }
        md.append('\n');
    }

    private void appendChartsFor(StringBuilder md, List<ChartImage> charts, String dumpIdentifier, File imagesDir) throws IOException {
        if (charts == null) return;
        for (ChartImage chart : charts) {
            if (!dumpIdentifier.equals(chart.dumpIdentifier) || chart.sourceFile == null || !chart.sourceFile.isFile()) continue;
            File dest = new File(imagesDir, chart.sourceFile.getName());
            Files.copy(chart.sourceFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            md.append("![").append(chart.caption != null ? chart.caption : "Heap Dump Overview")
                    .append("](images/").append(dest.getName()).append(")\n\n");
            md.append("*").append(chart.caption != null ? chart.caption : "")
                    .append(" -- MAT(Eclipse Memory Analyzer)이 실제 분석 결과로 직접 생성한 그래프입니다.*\n\n");
        }
    }

    private void appendLeakSuspects(StringBuilder md, EvidenceBundle.DumpEvidence dump) {
        if (dump.leakSuspects == null || dump.leakSuspects.isEmpty()) {
            md.append("MAT가 이 덤프에서 별도의 leak suspect를 보고하지 않았습니다.\n\n");
            return;
        }
        for (EvidenceBundle.LeakSuspectEvidence s : dump.leakSuspects) {
            md.append("#### ").append(s.title).append(" (").append(s.suspectIdentifier).append(")\n\n");
            md.append("- 점유 주체: ").append(nullSafe(s.holderIdentity)).append('\n');
            if (s.threadName != null) md.append("- 스레드명: ").append(s.threadName).append('\n');
            md.append("- Retained Size: ").append(formatBytes(s.retainedBytes)).append(" (").append(formatPercent(s.retainedPercent)).append(")\n");
            if (s.accumulationPointClass != null) {
                md.append("- 누적 지점 클래스: ").append(s.accumulationPointClass)
                        .append(" -- ").append(formatBytes(s.accumulationPointBytes)).append(" (").append(formatPercent(s.accumulationPointPercent)).append(")\n");
            }
            md.append('\n');
            if (s.significantStackFrames != null && !s.significantStackFrames.isEmpty()) {
                md.append("주요 스택 프레임(MAT 추출):\n\n```text\n");
                s.significantStackFrames.forEach(f -> md.append(f).append('\n'));
                md.append("```\n\n");
            }
            if (s.fullStackTrace != null && !s.fullStackTrace.isEmpty()) {
                md.append("실행 스택(전체, MAT 추출):\n\n```text\n");
                s.fullStackTrace.forEach(f -> md.append(f).append('\n'));
                md.append("```\n\n");
            } else if (s.stackExtractionNote != null) {
                md.append("> 실행 스택: ").append(s.stackExtractionNote).append("\n\n");
            }
        }
    }

    private void appendThreadSection(StringBuilder md, EvidenceBundle.DumpEvidence dump) {
        if (dump.threads == null || dump.threads.isEmpty()) {
            md.append("스레드 정보를 추출하지 못했습니다.\n\n");
            return;
        }
        if (dump.deadlockCycles != null && !dump.deadlockCycles.isEmpty()) {
            md.append("**교착상태(Deadlock) 의심 발견** -- 아래 스레드들이 서로의 락을 기다리는 순환 구조가 실제로 확인되었습니다:\n\n");
            for (List<String> cycle : dump.deadlockCycles) {
                md.append("- ").append(String.join(" → ", cycle)).append(" → ").append(cycle.get(0)).append('\n');
            }
            md.append('\n');
        }
        md.append("| 스레드명 | 상태 |\n|---|---|\n");
        for (EvidenceBundle.ThreadEvidence t : dump.threads) {
            md.append("| ").append(t.name).append(" | ").append(nullSafe(t.state)).append(" |\n");
        }
        md.append('\n');
    }

    private void appendConclusion(StringBuilder md, String claudeConclusionMarkdown) {
        md.append("## 4. 결론 및 권고\n\n");
        if (claudeConclusionMarkdown == null || claudeConclusionMarkdown.isBlank()) {
            md.append("결론 초안을 생성하지 못했습니다. 관리자 확인이 필요합니다.\n\n");
        } else {
            md.append(claudeConclusionMarkdown.trim()).append("\n\n");
        }
    }

    private void appendEvidenceReferences(StringBuilder md, EvidenceBundle bundle, File evidenceDir) throws IOException {
        md.append("## 5. 근거 파일 참조\n\n");
        md.append("- evidence/evidence.json: 이 보고서 작성에 사용된 전체 구조화 근거 데이터\n");
        for (EvidenceBundle.DumpEvidence dump : bundle.dumps) {
            md.append("- ").append(dump.dumpIdentifier).append(" (").append(dump.dumpKind).append(")\n");
        }
        if (bundle.serverLog != null) {
            md.append("- ").append(bundle.serverLog.logFileName).append(" (서버 로그)");
            if (!bundle.serverLog.windowAndKeywordOverlap && bundle.serverLog.note != null) {
                md.append(" -- ").append(bundle.serverLog.note);
            }
            md.append('\n');
        }
        md.append('\n');
    }

    private static String nullSafe(String v) { return v == null ? "미상" : v; }
    private static String formatPercent(Double v) { return v == null ? "미상" : String.format(Locale.US, "%.2f%%", v); }
    private static String formatBytes(Long bytes) {
        if (bytes == null) return "미상";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 0.1) return String.format(Locale.US, "%,d bytes (%.2f GB)", bytes, gb);
        double mb = bytes / (1024.0 * 1024.0);
        return String.format(Locale.US, "%,d bytes (%.1f MB)", bytes, mb);
    }
}
