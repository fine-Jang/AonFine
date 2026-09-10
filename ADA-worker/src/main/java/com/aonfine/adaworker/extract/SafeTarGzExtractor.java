package com.aonfine.adaworker.extract;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Extracts a validated dump tar.gz into an attempt-scoped directory. The original archive is
 * never modified or deleted; every entry is re-checked for path safety here even though the
 * portal already validated it at upload time (this Worker never trusts data that crossed a
 * process boundary without re-verifying it). Mirrors the limits/behavior of
 * com.aonfine.ada.upload.TarGzStreamValidator + PathSafetyUtil, but this one actually writes
 * files (the upload-time validator deliberately never does).
 */
public final class SafeTarGzExtractor {

    public static final class Limits {
        public int maxEntries = 200_000;
        public long maxDecompressedTotalBytes = 107_374_182_400L; // 100GB
        public long maxCompressionRatio = 300;
        public long maxExtractionMillis = 30 * 60 * 1000L; // 30 minutes
    }

    public static final class ExtractedFile {
        public final String entryName;
        public final File file;
        public final long size;
        ExtractedFile(String entryName, File file, long size) { this.entryName = entryName; this.file = file; this.size = size; }
    }

    private final Limits limits;

    public SafeTarGzExtractor(Limits limits) {
        this.limits = limits;
    }

    /** @param destDir must already exist and be empty; every extracted path is verified to stay under it. */
    public List<ExtractedFile> extract(File tarGz, File destDir) throws IOException {
        String destCanonical = destDir.getCanonicalPath() + File.separator;
        List<ExtractedFile> out = new ArrayList<>();
        long started = System.currentTimeMillis();
        long compressedSize = Math.max(1, tarGz.length());
        long totalDecompressed = 0;
        int entryCount = 0;
        byte[] buf = new byte[256 * 1024];

        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(tarGz), 256 * 1024);
             GZIPInputStream gzipIn = new GZIPInputStream(fileIn, 64 * 1024);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                entryCount++;
                if (entryCount > limits.maxEntries) {
                    throw new IOException("tar entry count exceeds limit (" + limits.maxEntries + "); refusing to continue extraction");
                }
                if (System.currentTimeMillis() - started > limits.maxExtractionMillis) {
                    throw new IOException("extraction exceeded time limit (" + limits.maxExtractionMillis + "ms)");
                }
                String name = entry.getName();
                if (isDangerousEntryName(name)) {
                    throw new IOException("refusing unsafe tar entry path: " + name);
                }
                if (entry.isSymbolicLink() || entry.isLink()) {
                    throw new IOException("refusing symlink/hardlink tar entry: " + name);
                }
                File target = new File(destDir, name);
                String targetCanonical = target.getCanonicalPath();
                if (entry.isDirectory()) {
                    if (!(targetCanonical + File.separator).startsWith(destCanonical) && !targetCanonical.equals(destDir.getCanonicalPath())) {
                        throw new IOException("refusing tar entry escaping destination directory: " + name);
                    }
                    Files.createDirectories(target.toPath());
                    continue;
                }
                if (!targetCanonical.startsWith(destCanonical)) {
                    throw new IOException("refusing tar entry escaping destination directory: " + name);
                }
                Files.createDirectories(target.getParentFile().toPath());

                long entrySize = 0;
                try (OutputStream out2 = new FileOutputStream(target)) {
                    int read;
                    while ((read = tarIn.read(buf)) != -1) {
                        entrySize += read;
                        totalDecompressed += read;
                        if (totalDecompressed > limits.maxDecompressedTotalBytes) {
                            throw new IOException("decompressed total size exceeds limit; possible decompression bomb");
                        }
                        if (totalDecompressed / compressedSize > limits.maxCompressionRatio) {
                            throw new IOException("compression ratio exceeds limit; possible decompression bomb");
                        }
                        if (System.currentTimeMillis() - started > limits.maxExtractionMillis) {
                            throw new IOException("extraction exceeded time limit (" + limits.maxExtractionMillis + "ms)");
                        }
                        out2.write(buf, 0, read);
                    }
                }
                out.add(new ExtractedFile(name, target, entrySize));
            }
        }
        return out;
    }

    static boolean isDangerousEntryName(String name) {
        if (name == null || name.isEmpty()) return true;
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/")) return true;
        if (normalized.indexOf('\0') >= 0) return true;
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) return true;
        }
        return normalized.length() >= 2 && normalized.charAt(1) == ':';
    }

    public static boolean isHprof(String entryName) {
        return entryName.toLowerCase(Locale.ROOT).endsWith(".hprof");
    }
}
