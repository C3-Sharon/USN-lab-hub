package com.usn.labhub.user.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMediaStorageAdapterTest {

    @TempDir
    Path temp;

    @Test
    void stagesAtSizeLimitAndCalculatesSha256() throws Exception {
        byte[] content = new byte[(int) LocalMediaStorageAdapter.MAX_BYTES];
        content[0] = 1;
        content[content.length - 1] = 2;
        LocalMediaStorageAdapter storage = storage();

        StagedMedia staged = storage.stage(new ByteArrayInputStream(content));

        assertEquals(content.length, staged.sizeBytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)), staged.sha256());
        assertEquals(1, regularFileCount(temp.resolve("staging")));
    }

    @Test
    void rejectsOversizedAndEmptyInputWithoutLeavingStagingFiles() throws Exception {
        LocalMediaStorageAdapter storage = storage();
        byte[] oversized = new byte[(int) LocalMediaStorageAdapter.MAX_BYTES + 1];

        MediaStorageException tooLarge = assertThrows(MediaStorageException.class,
                () -> storage.stage(new ByteArrayInputStream(oversized)));
        assertEquals(MediaStorageException.Reason.TOO_LARGE, tooLarge.getReason());
        assertEquals(0, regularFileCount(temp.resolve("staging")));

        MediaStorageException empty = assertThrows(MediaStorageException.class,
                () -> storage.stage(new ByteArrayInputStream(new byte[0])));
        assertEquals(MediaStorageException.Reason.TYPE_UNSUPPORTED, empty.getReason());
        assertEquals(0, regularFileCount(temp.resolve("staging")));
    }

    @Test
    void rejectsUncontrolledObjectKeysBeforeFilesystemAccess() {
        LocalMediaStorageAdapter storage = storage();
        List<String> invalidKeys = List.of(
                "../original/00000000-0000-0000-0000-000000000000.jpg",
                "C:/media/00000000-0000-0000-0000-000000000000.jpg",
                "original\\00000000-0000-0000-0000-000000000000.jpg",
                "staging/00000000-0000-0000-0000-000000000000.jpg",
                "unknown/00000000-0000-0000-0000-000000000000.jpg");

        for (String key : invalidKeys) {
            MediaStorageException exception = assertThrows(MediaStorageException.class, () -> storage.open(key));
            assertEquals(MediaStorageException.Reason.INVALID_OBJECT_KEY, exception.getReason(), key);
        }
    }

    @Test
    void commitsOriginalAndDisplayAndOpensOriginalByLogicalKey() throws Exception {
        LocalMediaStorageAdapter storage = storage();
        byte[] original = imageBytes("png", 5, 4);

        CommittedMedia committed = storage.commit(
                storage.stage(new ByteArrayInputStream(original)),
                new MediaUploadDescriptor("board.png", "image/png"));

        assertTrue(committed.originalObjectKey().matches("original/[0-9a-f-]{36}\\.png"));
        assertTrue(committed.displayObjectKey().matches("display/[0-9a-f-]{36}\\.png"));
        assertFalse(committed.originalObjectKey().contains("board"));
        try (var input = storage.open(committed.originalObjectKey())) {
            assertArrayEquals(original, input.readAllBytes());
        }
        try (var input = storage.open(committed.displayObjectKey())) {
            assertNotNull(ImageIO.read(input));
        }
        assertEquals(0, regularFileCount(temp.resolve("staging")));
    }

    @Test
    void cleansAllFilesWhenFirstOrSecondAtomicMoveFails() throws Exception {
        for (int failureCall : List.of(1, 2)) {
            Path root = temp.resolve("failure-" + failureCall);
            LocalMediaStorageAdapter storage = storage(root, new FailingMover(failureCall));
            byte[] original = imageBytes("jpg", 3, 3);
            StagedMedia staged = storage.stage(new ByteArrayInputStream(original));

            MediaStorageException exception = assertThrows(MediaStorageException.class, () -> storage.commit(
                    staged, new MediaUploadDescriptor("module.jpg", "image/jpeg")));

            assertEquals(MediaStorageException.Reason.STORAGE_FAILED, exception.getReason());
            assertEquals(0, regularFileCount(root.resolve("staging")));
            assertEquals(0, regularFileCount(root.resolve("original")));
            assertEquals(0, regularFileCount(root.resolve("display")));
        }
    }

    @Test
    void rollbackAcceptsOnlyLocalHandleAndIsIdempotent() throws Exception {
        LocalMediaStorageAdapter storage = storage();
        CommittedMedia committed = storage.commit(
                storage.stage(new ByteArrayInputStream(imageBytes("png", 2, 2))),
                new MediaUploadDescriptor("component.png", "image/png"));
        CommittedMedia foreign = new ForeignCommittedMedia(
                committed.originalObjectKey(), committed.displayObjectKey());

        MediaStorageException rejected = assertThrows(MediaStorageException.class,
                () -> storage.rollbackNewObject(foreign));
        assertEquals(MediaStorageException.Reason.INVALID_OBJECT_KEY, rejected.getReason());
        assertEquals(1, regularFileCount(temp.resolve("original")));
        assertEquals(1, regularFileCount(temp.resolve("display")));

        storage.rollbackNewObject(committed);
        storage.rollbackNewObject(committed);
        assertEquals(0, regularFileCount(temp.resolve("original")));
        assertEquals(0, regularFileCount(temp.resolve("display")));
    }

    @Test
    void clearsOnlyStagingFilesAtStartupAndFailsOnUnexpectedEntries() throws Exception {
        Files.createDirectories(temp.resolve("staging"));
        Files.createDirectories(temp.resolve("original"));
        Files.createDirectories(temp.resolve("display"));
        Files.writeString(temp.resolve("staging/stale.tmp"), "stale");
        Files.writeString(temp.resolve("original/keep.jpg"), "original");
        Files.writeString(temp.resolve("display/keep.jpg"), "display");

        storage();

        assertEquals(0, regularFileCount(temp.resolve("staging")));
        assertTrue(Files.exists(temp.resolve("original/keep.jpg")));
        assertTrue(Files.exists(temp.resolve("display/keep.jpg")));

        Files.createDirectory(temp.resolve("staging/unexpected"));
        MediaStorageException exception = assertThrows(MediaStorageException.class, this::storage);
        assertEquals(MediaStorageException.Reason.STORAGE_FAILED, exception.getReason());
    }

    private LocalMediaStorageAdapter storage() {
        return storage(temp, new NioMediaFileMover());
    }

    private LocalMediaStorageAdapter storage(Path root, MediaFileMover mover) {
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setStorageRoot(root.toString());
        return new LocalMediaStorageAdapter(properties, new MediaImageProcessor(), mover);
    }

    private long regularFileCount(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private byte[] imageBytes(String format, int width, int height) throws Exception {
        int imageType = "jpg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(width, height, imageType);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(20, 120, 200, 180));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IllegalStateException("Missing ImageIO writer for " + format);
        }
        return output.toByteArray();
    }

    private static final class FailingMover implements MediaFileMover {
        private final int failureCall;
        private final MediaFileMover delegate = new NioMediaFileMover();
        private int calls;

        private FailingMover(int failureCall) {
            this.failureCall = failureCall;
        }

        @Override
        public void moveAtomically(Path source, Path target) throws IOException {
            calls++;
            if (calls == failureCall) {
                throw new IOException("fixed move failure " + failureCall);
            }
            delegate.moveAtomically(source, target);
        }
    }

    private record ForeignCommittedMedia(String originalObjectKey, String displayObjectKey)
            implements CommittedMedia {
        @Override public long sizeBytes() { return 1; }
        @Override public String sha256() { return "foreign"; }
        @Override public String contentType() { return "image/png"; }
        @Override public int width() { return 1; }
        @Override public int height() { return 1; }
    }
}
