package com.aonfine.ada.upload;

import static org.junit.Assert.*;
import static com.aonfine.ada.dump.DumpVO.*;
import static com.aonfine.ada.LocalDatabase.inject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.sql.DataSource;
import org.apache.commons.compress.archivers.tar.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockHttpServletRequest;
import com.aonfine.ada.LocalDatabase;
import com.aonfine.ada.common.*;
import com.aonfine.ada.dump.impl.DumpJdbcMapper;

public class UploadLifecycleTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private DumpUploadService service;
    private HookMapper mapper;
    private AdaProperties props;
    private byte[] valid;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Before public void setup() throws Exception {
        DataSource ds = LocalDatabase.create();
        mapper = new HookMapper(); mapper.setDataSource(ds);
        props = new AdaProperties(); props.setStorageRoot(temp.newFolder("storage").getPath());
        props.setNfsMarkerFile(temp.newFile("marker").getPath());
        props.setMaxFileSizeBytes(4999999999L); props.setMinFreeSpaceReserveBytes(0);
        props.setSpaceCheckIntervalBytes(1024); props.setMaxTarEntries(100);
        props.setMaxDecompressedTotalBytes(10000000); props.setMaxCompressionRatio(10000); props.setMaxValidationSeconds(30);
        NfsMountGuard guard = new NfsMountGuard(); inject(guard, "adaProperties", props);
        StorageSpaceChecker space = new StorageSpaceChecker(); inject(space, "adaProperties", props);
        service = new DumpUploadService(); inject(service, "adaProperties", props); inject(service, "nfsMountGuard", guard);
        inject(service, "storageSpaceChecker", space); inject(service, "dumpMapper", mapper); service.init();
        // StorageSpaceChecker requires the root, provided above; archive includes a real signature.
        valid = archive();
    }
    @After public void teardown() { service.stop(); executor.shutdownNow(); }
    public DumpUploadService service() { return service; }

    private String prepare(byte[] content) throws Exception {
        return service.prepare("alice", "THREAD", "dept", "task", "host", "threads.tar.gz", content.length).getDumpId();
    }
    private byte[] archive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(out))) {
            byte[] content = ("Full thread dump OpenJDK 64-Bit Server VM (25.352 mixed mode):\n"
                    + "\"main\" #1 prio=5 tid=0x1 nid=0x1 runnable\n   java.lang.Thread.State: RUNNABLE\n"
                    + "\tat java.lang.Thread.run(Thread.java:750)\n").getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry entry = new TarArchiveEntry("threads.txt"); entry.setSize(content.length);
            tar.putArchiveEntry(entry); tar.write(content); tar.closeArchiveEntry(); tar.finish();
        }
        return out.toByteArray();
    }
    private byte[] body(byte[] content) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(("--ada-boundary\r\nContent-Disposition: form-data; name=\"dumpFile\"; filename=\"threads.tar.gz\"\r\n"
                + "Content-Type: application/gzip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        b.write(content); b.write("\r\n--ada-boundary--\r\n".getBytes(StandardCharsets.UTF_8)); return b.toByteArray();
    }
    private MockHttpServletRequest request(byte[] content) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/dump/upload.do");
        req.setContentType("multipart/form-data; boundary=ada-boundary"); req.setContent(body(content)); return req;
    }
    private void noFiles(String id) {
        assertFalse(new File(props.getStagingDir(), id + ".tar.gz.part").exists());
        assertFalse(new File(props.getStoreDir(), id + ".tar.gz").exists());
    }

    @Test public void readyCancellationFencesLateBodyAndIsIdempotent() throws Exception {
        String id = prepare(valid);
        assertEquals(STATUS_CANCELLED, service.cancel(id).getStatusCd());
        assertEquals(STATUS_CANCELLED, service.cancel(id).getStatusCd());
        assertEquals(STATUS_CANCELLED, service.handleUpload(request(valid), id).getStatusCd()); noFiles(id);
        assertNotEquals(id, prepare(valid));
    }
    @Test public void successCannotBeDeletedByLateCancelOrDuplicateBody() throws Exception {
        String id = prepare(valid);
        assertEquals(STATUS_STORED, service.handleUpload(request(valid), id).getStatusCd());
        byte[] stored = Files.readAllBytes(new File(props.getStoreDir(), id + ".tar.gz").toPath());
        assertEquals(STATUS_STORED, service.cancel(id).getStatusCd());
        assertEquals(STATUS_STORED, service.handleUpload(request(valid), id).getStatusCd());
        assertArrayEquals(valid, stored);
        assertArrayEquals(valid, Files.readAllBytes(new File(props.getStoreDir(), id + ".tar.gz").toPath()));
    }
    @Test public void invalidArchiveFailsWithReasonAndPermitsNewId() throws Exception {
        byte[] invalid = "not gzip".getBytes(StandardCharsets.UTF_8); String id = prepare(invalid);
        UploadResult result = service.handleUpload(request(invalid), id);
        assertEquals(STATUS_FAILED, result.getStatusCd()); assertTrue(result.isRetryAllowed());
        assertTrue(result.getMessage().contains("손상")); noFiles(id); assertNotEquals(id, prepare(valid));
    }
    @Test public void truncatedTransferIsNotStored() throws Exception {
        String id = prepare(valid);
        assertEquals(STATUS_FAILED, service.handleUpload(request(new byte[5]), id).getStatusCd()); noFiles(id);
    }
    @Test public void cancelDuringValidationStopsActualValidatorAndCleansFile() throws Exception {
        String id = prepare(valid);
        inject(service, "validator", new TarGzStreamValidator(props) {
            @Override public Result validate(File f, String type, UploadCancellation c) throws AdaStorageException {
                service.cancel(id);
                return super.validate(f, type, c);
            }
        });
        assertEquals(STATUS_CANCELLED, service.handleUpload(request(valid), id).getStatusCd()); noFiles(id);
    }
    @Test public void cancellationWinsImmediatelyBeforeFinalize() throws Exception {
        String id = prepare(valid);
        mapper.beforeFinalize = () -> service.cancel(id);
        assertEquals(STATUS_CANCELLED, service.handleUpload(request(valid), id).getStatusCd()); noFiles(id);
    }
    @Test public void finalizeWinsBeforeCancellation() throws Exception {
        String id = prepare(valid);
        mapper.afterFinalize = () -> assertEquals(STATUS_FINALIZING, service.cancel(id).getStatusCd());
        assertEquals(STATUS_STORED, service.handleUpload(request(valid), id).getStatusCd());
        assertTrue(new File(props.getStoreDir(), id + ".tar.gz").exists());
    }
    @Test public void lostDbCommitResponsePreservesAlreadyStoredFile() throws Exception {
        String id = prepare(valid); mapper.throwAfterStore = true;
        assertEquals(STATUS_STORED, service.handleUpload(request(valid), id).getStatusCd());
        assertTrue(new File(props.getStoreDir(), id + ".tar.gz").exists());
    }
    @Test public void rejectedDbStoreCleansMovedFinalFile() throws Exception {
        String id = prepare(valid); mapper.throwBeforeStore = true;
        assertEquals(STATUS_FAILED, service.handleUpload(request(valid), id).getStatusCd()); noFiles(id);
    }
    @Test public void dbUnavailableAfterCommitLeavesFileAndReportsUnknown() throws Exception {
        String id = prepare(valid); mapper.unavailableAfterStore = true;
        UploadResult result = service.handleUpload(request(valid), id);
        assertEquals("UNKNOWN", result.getStatusCd()); assertFalse(result.isTerminal()); assertFalse(result.isRetryAllowed());
        assertTrue(new File(props.getStoreDir(), id + ".tar.gz").exists());
        mapper.unavailable = false;
        assertEquals(STATUS_STORED, service.status(id).getStatusCd());
    }
    @Test public void staleBodyAfterRestartIsNeverDeclaredCancelledWithoutOwnerConfirmation() throws Exception {
        String id = prepare(valid);
        mapper.transition(id, STATUS_READY, STATUS_UPLOADING, null);
        assertEquals(STATUS_CANCEL_REQUESTED, service.cancel(id).getStatusCd());
        assertFalse(service.status(id).isTerminal());
    }
    @Test public void cleanupFailureNeverReportsCancelledOrAllowsRetry() throws Exception {
        String id = prepare(valid);
        File conflict = new File(props.getStagingDir(), id + ".tar.gz.part"); assertTrue(conflict.mkdirs());
        assertTrue(new File(conflict, "cannot-delete-directory").createNewFile());
        UploadResult result = service.cancel(id);
        assertEquals(STATUS_CLEANUP_FAILED, result.getStatusCd()); assertFalse(result.isTerminal()); assertFalse(result.isRetryAllowed());
        try { prepare(valid); fail("unresolved cleanup must block"); } catch (AdaStorageException expected) { }
    }
    @Test public void concurrentPrepareAllowsOnlyOneActiveUpload() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> call = () -> { start.await(); try { prepare(valid); return true; } catch (AdaStorageException e) { return false; } };
        Future<Boolean> a = executor.submit(call), b = executor.submit(call); start.countDown();
        assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
    }
    @Test public void blockedNetworkReadMustExitBeforeCancellationCompletes() throws Exception {
        byte[] large = new byte[300000]; String id = prepare(large);
        BlockingInput input = new BlockingInput(body(large), false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/dump/upload.do") {
            @Override public ServletInputStream getInputStream() { return input; }
        };
        req.setContentType("multipart/form-data; boundary=ada-boundary");
        Future<UploadResult> running = executor.submit(() -> service.handleUpload(req, id));
        assertTrue(input.blocked.await(5, TimeUnit.SECONDS));
        assertEquals(STATUS_CANCEL_REQUESTED, service.cancel(id).getStatusCd());
        assertFalse(service.status(id).isTerminal());
        input.release.countDown();
        assertEquals(STATUS_CANCELLED, running.get(5, TimeUnit.SECONDS).getStatusCd()); noFiles(id);
    }
    @Test public void serverClosesCancellableNetworkStream() throws Exception {
        byte[] large = new byte[300000]; String id = prepare(large);
        BlockingInput input = new BlockingInput(body(large), true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/dump/upload.do") {
            @Override public ServletInputStream getInputStream() { return input; }
        };
        req.setContentType("multipart/form-data; boundary=ada-boundary");
        Future<UploadResult> running = executor.submit(() -> service.handleUpload(req, id));
        assertTrue(input.blocked.await(5, TimeUnit.SECONDS)); service.cancel(id);
        assertEquals(STATUS_CANCELLED, running.get(5, TimeUnit.SECONDS).getStatusCd()); assertTrue(input.closed.get()); noFiles(id);
    }
    private static class BlockingInput extends ServletInputStream {
        final byte[] bytes; int offset; final boolean closeUnblocks;
        final CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean closed = new AtomicBoolean();
        BlockingInput(byte[] bytes, boolean closeUnblocks) { this.bytes = bytes; this.closeUnblocks = closeUnblocks; }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (offset >= 8192) {
                blocked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("test timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                throw new IOException("connection closed");
            }
            int n = Math.min(len, 8192 - offset); System.arraycopy(bytes, offset, b, off, n); offset += n; return n;
        }
        @Override public int read() throws IOException { byte[] b = new byte[1]; return read(b, 0, 1) < 0 ? -1 : b[0] & 255; }
        @Override public void close() { closed.set(true); if (closeUnblocks) release.countDown(); }
        @Override public boolean isFinished() { return false; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener r) { throw new UnsupportedOperationException(); }
    }
    private static class HookMapper extends DumpJdbcMapper {
        Runnable beforeFinalize, afterFinalize;
        boolean throwBeforeStore, throwAfterStore, unavailableAfterStore, unavailable;
        @Override public com.aonfine.ada.dump.DumpVO selectOne(String id, java.sql.Timestamp cutoff) {
            if (unavailable) throw new IllegalStateException("simulated database unavailable");
            return super.selectOne(id, cutoff);
        }
        @Override public int transition(String id, String from, String to, String reason) {
            if (STATUS_FINALIZING.equals(to) && beforeFinalize != null) beforeFinalize.run();
            int changed = super.transition(id, from, to, reason);
            if (STATUS_FINALIZING.equals(to) && changed == 1 && afterFinalize != null) afterFinalize.run();
            return changed;
        }
        @Override public int markStored(String id, String original, String stored, long bytes) {
            if (throwBeforeStore) throw new IllegalStateException("simulated DB failure");
            int changed = super.markStored(id, original, stored, bytes);
            if (unavailableAfterStore) { unavailable = true; throw new IllegalStateException("simulated connection loss"); }
            if (throwAfterStore) throw new IllegalStateException("simulated lost commit reply");
            return changed;
        }
    }
}
