package com.aonfine.adaworker.mat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Invokes the Worker host's already-installed MAT batch wrapper (run-mat-batch.sh, which itself
 * pins JAVA_HOME to /DATA/Work/tools/jdk-21.0.12.1_1 and execs MemoryAnalyzer -- see that script).
 * Arguments are passed as an array, never concatenated into a shell string. cwd is the attempt's
 * own extract directory so MAT's *_Leak_Suspects.zip / *_System_Overview.zip land next to the hprof.
 */
public final class MatRunner {

    public static final class MatRunResult {
        public int exitCode;
        public String logTail;
        public File leakSuspectsZip;
        public File systemOverviewZip;
    }

    private final String matBatchScript;
    private final long timeoutSeconds;

    public MatRunner(String matBatchScript, long timeoutSeconds) {
        this.matBatchScript = matBatchScript;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** @param hprofFile must already exist under cwd (or a subdirectory of it); reports are written next to it by MAT itself. */
    public MatRunResult run(File hprofFile, File cwd) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(matBatchScript);
        command.add(hprofFile.getAbsolutePath());
        command.add("org.eclipse.mat.api:suspects");
        command.add("org.eclipse.mat.api:overview");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cwd);
        pb.redirectErrorStream(true);
        File logFile = new File(cwd, "mat-run.log");
        pb.redirectOutput(ProcessBuilder.Redirect.to(logFile));
        // Minimal environment: no ambient credentials/PATH surprises from the Worker's own shell profile.
        pb.environment().clear();
        pb.environment().put("PATH", "/usr/bin:/bin");

        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("MAT batch analysis exceeded " + timeoutSeconds + "s timeout and was killed");
        }
        MatRunResult result = new MatRunResult();
        result.exitCode = process.exitValue();
        result.logTail = tailOf(logFile, 4000);

        String baseName = stripExtension(hprofFile.getName());
        File leak = new File(cwd, baseName + "_Leak_Suspects.zip");
        File overview = new File(cwd, baseName + "_System_Overview.zip");
        if (leak.isFile()) result.leakSuspectsZip = leak;
        if (overview.isFile()) result.systemOverviewZip = overview;
        return result;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String tailOf(File file, int maxChars) throws IOException {
        if (!file.isFile()) return "";
        String content = new String(Files.readAllBytes(file.toPath()));
        return content.length() > maxChars ? content.substring(content.length() - maxChars) : content;
    }
}
