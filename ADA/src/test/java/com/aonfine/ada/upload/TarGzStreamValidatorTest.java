package com.aonfine.ada.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.aonfine.ada.common.AdaProperties;
import com.aonfine.ada.common.AdaStorageException;
import com.aonfine.ada.dump.DumpVO;

public class TarGzStreamValidatorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private AdaProperties defaultProps() {
        AdaProperties props = new AdaProperties();
        props.setMaxTarEntries(1000);
        props.setMaxDecompressedTotalBytes(100L * 1024 * 1024);
        props.setMaxCompressionRatio(1000);
        props.setMaxValidationSeconds(30);
        return props;
    }

    private File buildTarGz(Map<String, byte[]> entries, boolean withDangerousSymlink) throws IOException {
        File file = tmp.newFile("test-" + System.nanoTime() + ".tar.gz");
        try (FileOutputStream fos = new FileOutputStream(file);
                GZIPOutputStream gzos = new GZIPOutputStream(fos);
                TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(e.getKey());
                entry.setSize(e.getValue().length);
                taos.putArchiveEntry(entry);
                taos.write(e.getValue());
                taos.closeArchiveEntry();
            }
            if (withDangerousSymlink) {
                TarArchiveEntry link = new TarArchiveEntry("evil-link", TarArchiveEntry.LF_SYMLINK);
                link.setLinkName("/etc/passwd");
                taos.putArchiveEntry(link);
                taos.closeArchiveEntry();
            }
            taos.finish();
        }
        return file;
    }

    /** 구조적으로 완결된 최소 HPROF 바이트열: 헤더 + 레코드 1개(태그/시간/길이/데이터)로 정상 종료한다. */
    private byte[] validHprofBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("JAVA PROFILE 1.0.1".getBytes(StandardCharsets.US_ASCII));
        out.write(0); // NUL 종료
        writeU4(out, 4); // identifier size
        for (int i = 0; i < 8; i++) {
            out.write(0); // timestamp
        }
        out.write(0x01); // record tag (STRING)
        writeU4(out, 0); // time delta
        byte[] data = "hello".getBytes(StandardCharsets.US_ASCII);
        writeU4(out, data.length);
        out.write(data);
        return out.toByteArray();
    }

    /** 레코드 길이 필드까지만 쓰고 실제 데이터는 잘려나간 손상된 HPROF. */
    private byte[] truncatedHprofBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("JAVA PROFILE 1.0.1".getBytes(StandardCharsets.US_ASCII));
        out.write(0);
        writeU4(out, 4);
        for (int i = 0; i < 8; i++) {
            out.write(0);
        }
        out.write(0x01);
        writeU4(out, 0);
        writeU4(out, 100); // 100바이트라고 선언하지만 실제로는 이어지는 데이터가 없음
        return out.toByteArray();
    }

    private void writeU4(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private byte[] validThreadDumpBytes() {
        String content = "2026-09-09 10:00:00\n"
                + "Full thread dump OpenJDK 64-Bit Server VM (25.352-b08 mixed mode):\n\n"
                + "\"main\" #1 prio=5 os_prio=0 tid=0x00007f0000000000 nid=0x1 runnable\n"
                + "   java.lang.Thread.State: RUNNABLE\n"
                + "\tat java.lang.Thread.run(Thread.java:750)\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    // ===================== HEAP =====================
    @Test
    public void cancellationInterruptsLargeEntryReads() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", validHprofBytes());
        byte[] data = new byte[2 * 1024 * 1024]; new java.util.Random(42).nextBytes(data);
        entries.put("large.log", data);
        File file = buildTarGz(entries, false);
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        try {
            new TarGzStreamValidator(defaultProps()).validate(file, DumpVO.DUMP_TYPE_HEAP, () -> {
                if (checks.incrementAndGet() >= 30) throw new UploadCancellation.Cancelled();
            });
            fail("must stop while reading the entry");
        } catch (UploadCancellation.Cancelled expected) { assertEquals(30, checks.get()); }
    }

    @Test
    public void cancellationAlsoInterruptsPostTarGzipDrain() throws Exception {
        File file = tmp.newFile("trailer.tar.gz");
        // Small tar block, followed by a large gzip payload after tar EOF.
        try (GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(file))) {
            TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip, 512);
            byte[] hprof = validHprofBytes(); TarArchiveEntry entry = new TarArchiveEntry("heap.hprof"); entry.setSize(hprof.length);
            tar.putArchiveEntry(entry); tar.write(hprof); tar.closeArchiveEntry(); tar.finish(); tar.flush();
            byte[] trailing = new byte[2 * 1024 * 1024]; new java.util.Random(43).nextBytes(trailing); gzip.write(trailing);
        }
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        try {
            new TarGzStreamValidator(defaultProps()).validate(file, DumpVO.DUMP_TYPE_HEAP, () -> {
                if (checks.incrementAndGet() >= 30) throw new UploadCancellation.Cancelled();
            });
            fail("gzip drain must observe cancellation");
        } catch (UploadCancellation.Cancelled expected) { assertEquals(30, checks.get()); }
    }

    @Test
    public void acceptsValidHeapArchiveWithHprofLogTxt() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", validHprofBytes());
        entries.put("app.log", "log line 1\nlog line 2\n".getBytes(StandardCharsets.UTF_8));
        entries.put("notes.txt", "notes".getBytes(StandardCharsets.UTF_8));
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        TarGzStreamValidator.Result result = validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);

        assertTrue(result.hprofFound);
        assertEquals(3, result.entryCount);
    }

    @Test
    public void rejectsHeapArchiveWithoutHprof() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("app.log", "no heap dump here".getBytes(StandardCharsets.UTF_8));
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("hprof"));
        }
    }

    @Test
    public void rejectsFakeHprofWithWrongMagicHeader() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", "this is just plain text, not a real heap dump".getBytes(StandardCharsets.UTF_8));
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException for fake hprof content");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("유효하지 않습니다"));
        }
    }

    @Test
    public void rejectsTruncatedHprof() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", truncatedHprofBytes());
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException for truncated hprof content");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("유효하지 않습니다"));
        }
    }

    @Test
    public void rejectsZeroLengthHprof() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", new byte[0]);
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException for zero-length hprof");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("유효하지 않습니다"));
        }
    }

    // ===================== THREAD =====================

    @Test
    public void acceptsValidThreadDumpArchive() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("threads.txt", validThreadDumpBytes());
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        TarGzStreamValidator.Result result = validator.validate(tarGz, DumpVO.DUMP_TYPE_THREAD);

        assertTrue(result.threadDumpFound);
    }

    @Test
    public void rejectsPlainTextMisidentifiedAsThreadDump() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("notes.txt", "this is just a regular note, not a thread dump".getBytes(StandardCharsets.UTF_8));
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_THREAD);
            fail("expected AdaStorageException for plain text misidentified as thread dump");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("스레드 덤프"));
        }
    }

    @Test
    public void rejectsHprofEntryInThreadTypeUpload() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", validHprofBytes());
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_THREAD);
            fail("expected AdaStorageException: hprof not allowed for THREAD type");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("지원하지 않는"));
        }
    }

    // ===================== 공통 (경로/구조/한도) =====================

    @Test
    public void rejectsPathTraversalEntry() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", validHprofBytes());
        entries.put("../../etc/passwd", "evil".getBytes(StandardCharsets.UTF_8));
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("경로"));
        }
    }

    @Test
    public void rejectsSymlinkEntry() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", validHprofBytes());
        File tarGz = buildTarGz(entries, true);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("특수 파일"));
        }
    }

    @Test
    public void rejectsDisallowedExtension() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", validHprofBytes());
        entries.put("payload.sh", "#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.UTF_8));
        File tarGz = buildTarGz(entries, false);

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("지원하지 않는"));
        }
    }

    @Test
    public void rejectsCorruptedGzip() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", validHprofBytes());
        File tarGz = buildTarGz(entries, false);

        // 파일 끝부분을 잘라내 gzip 트레일러(CRC32/ISIZE)와 deflate 스트림 자체를 불완전하게 만든다.
        // 중간 바이트를 뒤집는 방식은 tar 헤더의 mtime(엔트리 생성 시각)이 실행마다 달라져
        // 압축 결과 바이트 배치가 흔들리는 바람에 훼손 여부가 실행마다 달라지는 문제가 있어 truncate로 대체했다.
        try (RandomAccessFile raf = new RandomAccessFile(tarGz, "rw")) {
            long newLength = raf.length() * 60 / 100;
            raf.setLength(newLength);
        }

        TarGzStreamValidator validator = new TarGzStreamValidator(defaultProps());
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException for corrupted archive");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("손상"));
        }
    }

    @Test
    public void rejectsWhenEntryCountExceedsLimit() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("heap.hprof", validHprofBytes());
        for (int i = 0; i < 5; i++) {
            entries.put("extra" + i + ".log", "x".getBytes(StandardCharsets.UTF_8));
        }
        File tarGz = buildTarGz(entries, false);

        AdaProperties props = defaultProps();
        props.setMaxTarEntries(2); // 일부러 낮게 설정
        TarGzStreamValidator validator = new TarGzStreamValidator(props);
        try {
            validator.validate(tarGz, DumpVO.DUMP_TYPE_HEAP);
            fail("expected AdaStorageException for too many entries");
        } catch (AdaStorageException e) {
            assertTrue(e.getUserMessage().contains("항목 수"));
        }
    }
}
