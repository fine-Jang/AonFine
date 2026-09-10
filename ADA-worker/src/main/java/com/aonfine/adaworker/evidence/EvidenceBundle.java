package com.aonfine.adaworker.evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full set of facts handed to Claude to draft a report from, and also embedded verbatim in
 * the final report's "근거 파일 참조" section. Every dump/suspect entry carries its own file-name
 * identifier so numbers from different dumps or different suspects within one dump are never
 * merged or treated as if they were the same figure.
 */
public final class EvidenceBundle {
    public Map<String, String> metadata = new LinkedHashMap<>(); // deptNm, taskNm, hostNm, originalFileNm, requestedBy, dumpId, capturedAt(if known)
    public List<DumpEvidence> dumps = new ArrayList<>();
    public LogEvidence serverLog; // null if no server log was included in the archive

    public static final class DumpEvidence {
        public String dumpIdentifier;      // e.g. the hprof/thread-dump file name inside the archive -- unique per dump
        public String dumpKind;            // "HEAP" or "THREAD"
        public Map<String, String> heapOverview;             // HEAP only, verbatim MAT key/value table
        public List<LeakSuspectEvidence> leakSuspects;        // HEAP only
        public List<ThreadEvidence> threads;                  // THREAD only
        public List<List<String>> deadlockCycles;             // THREAD only, real cycles found in this dump's own lock graph
        public String note; // e.g. "MAT 배치 분석 실패: ..." or "이 덤프에는 스레드 스택이 없어 추출 불가"
    }

    public static final class LeakSuspectEvidence {
        public String suspectIdentifier; // e.g. "aonfine_20260909/java_pid_....hprof#ProblemSuspect1" -- unique within the whole bundle
        public String title;
        public String holderIdentity;
        public String threadName;
        public Long retainedBytes;
        public Double retainedPercent;
        public String accumulationPointClass;
        public Long accumulationPointBytes;
        public Double accumulationPointPercent;
        public List<String> keywords;
        public List<String> significantStackFrames;
        public List<String> fullStackTrace; // real frames from the linked stack-trace page, or empty if not extractable
        public String stackExtractionNote;  // e.g. "이 힙덤프에는 스레드 스택 정보가 없어 추출 불가"
    }

    public static final class ThreadEvidence {
        public String threadIdentifier; // dump file + thread name, unique within the bundle
        public String name;
        public String state;
        public List<String> stackFrames;
    }

    public static final class LogEvidence {
        public String logFileName;
        public String candidateTimestampUsed;
        public boolean windowAndKeywordOverlap;
        public List<String> windowExcerpt;
        public List<String> keywordExcerpt;
        public String note; // e.g. mismatch warning when window/keyword evidence disagree
    }
}
