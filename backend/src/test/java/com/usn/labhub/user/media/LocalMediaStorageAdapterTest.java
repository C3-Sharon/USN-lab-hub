package com.usn.labhub.user.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private LocalMediaStorageAdapter storage() {
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setStorageRoot(temp.toString());
        return new LocalMediaStorageAdapter(properties);
    }

    private long regularFileCount(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}
