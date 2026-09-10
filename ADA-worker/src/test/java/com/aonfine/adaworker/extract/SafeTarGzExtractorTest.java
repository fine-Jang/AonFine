package com.aonfine.adaworker.extract;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Test;

public class SafeTarGzExtractorTest {

    private File buildTarGz(String... nameAndContent) throws IOException {
        File f = File.createTempFile("test", ".tar.gz");
        f.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(f);
             GZIPOutputStream gz = new GZIPOutputStream(fos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            for (int i = 0; i < nameAndContent.length; i += 2) {
                String name = nameAndContent[i];
                byte[] content = nameAndContent[i + 1].getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(name);
                entry.setSize(content.length);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        }
        return f;
    }

    @Test public void extractsSafeEntriesUnderDestDir() throws Exception {
        File tarGz = buildTarGz("dump/heap.hprof", "fake-hprof-bytes", "dump/gc.log", "gc log content");
        File destDir = Files.createTempDirectory("extract-ok").toFile();
        List<SafeTarGzExtractor.ExtractedFile> files = new SafeTarGzExtractor(new SafeTarGzExtractor.Limits()).extract(tarGz, destDir);
        assertEquals(2, files.size());
        assertTrue(new File(destDir, "dump/heap.hprof").exists());
        assertEquals("fake-hprof-bytes", new String(Files.readAllBytes(new File(destDir, "dump/heap.hprof").toPath()), StandardCharsets.UTF_8));
    }

    @Test public void rejectsParentDirectoryTraversalEntry() throws Exception {
        File tarGz = buildTarGz("../../etc/passwd", "malicious");
        File destDir = Files.createTempDirectory("extract-traversal").toFile();
        try {
            new SafeTarGzExtractor(new SafeTarGzExtractor.Limits()).extract(tarGz, destDir);
            fail("expected rejection of path traversal entry");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unsafe"));
        }
        assertEquals(0, destDir.list().length);
    }

    @Test public void rejectsAbsolutePathEntry() throws Exception {
        // TarArchiveEntry(String) silently strips a leading '/' on write, so build this entry with
        // preserveLeadingSlashes=true to actually produce the absolute-path bytes a crafted archive would contain.
        File f = File.createTempFile("test-abs", ".tar.gz");
        f.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(f);
             GZIPOutputStream gz = new GZIPOutputStream(fos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            byte[] content = "malicious".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry("/etc/passwd", true);
            entry.setSize(content.length);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
        }
        File destDir = Files.createTempDirectory("extract-abs").toFile();
        try {
            new SafeTarGzExtractor(new SafeTarGzExtractor.Limits()).extract(f, destDir);
            fail("expected rejection of absolute path entry");
        } catch (IOException expected) { /* ok */ }
    }

    @Test public void rejectsEntryCountAboveLimit() throws Exception {
        String[] pairs = new String[6];
        pairs[0] = "a.txt"; pairs[1] = "x";
        pairs[2] = "b.txt"; pairs[3] = "y";
        pairs[4] = "c.txt"; pairs[5] = "z";
        File tarGz = buildTarGz(pairs);
        File destDir = Files.createTempDirectory("extract-limit").toFile();
        SafeTarGzExtractor.Limits limits = new SafeTarGzExtractor.Limits();
        limits.maxEntries = 2;
        try {
            new SafeTarGzExtractor(limits).extract(tarGz, destDir);
            fail("expected rejection when entry count exceeds limit");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("entry count"));
        }
    }
}
