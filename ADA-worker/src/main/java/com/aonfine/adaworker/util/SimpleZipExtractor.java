package com.aonfine.adaworker.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Unzips MAT's own batch report output (a zip Worker generated itself, not user-uploaded data). */
public final class SimpleZipExtractor {
    private SimpleZipExtractor() { }

    public static void extract(File zipFile, File destDir) throws IOException {
        Files.createDirectories(destDir.toPath());
        String destCanonical = destDir.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry entry;
            byte[] buf = new byte[64 * 1024];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName() == null || entry.getName().isEmpty()) continue;
                File target = new File(destDir, entry.getName());
                String targetCanonical = target.getCanonicalPath();
                if (!targetCanonical.startsWith(destCanonical) && !targetCanonical.equals(destDir.getCanonicalPath())) {
                    continue; // defensively skip; MAT never produces these, but never trust a zip blindly either
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target.toPath());
                    continue;
                }
                Files.createDirectories(target.getParentFile().toPath());
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    int read;
                    while ((read = zis.read(buf)) != -1) fos.write(buf, 0, read);
                }
            }
        }
    }
}
