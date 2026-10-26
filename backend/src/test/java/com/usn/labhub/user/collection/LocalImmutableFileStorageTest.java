package com.usn.labhub.user.collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalImmutableFileStorageTest {
    @TempDir Path temp;

    @Test
    void validatesCsvEncodingAndRollsBackOnlyNewCommittedFile() throws Exception {
        LocalImmutableFileStorage storage = new LocalImmutableFileStorage(temp.toString());
        CollectionApiException encoding = assertThrows(CollectionApiException.class, () -> storage.stage(
                new MockMultipartFile("file", "bad.csv", "text/csv", new byte[]{(byte) 0xC3, 0x28})));
        assertEquals("FILE_ENCODING_UNSUPPORTED", encoding.getReason());

        StagedCollectionFile staged = storage.stage(new MockMultipartFile("file", "ok.csv", "text/csv",
                "time,value\n2026-10-24,5.1\n".getBytes(StandardCharsets.UTF_8)));
        CommittedCollectionFile committed = storage.commit(7L, staged);
        assertTrue(Files.exists(committed.path()));
        assertTrue(committed.path().startsWith(temp.toAbsolutePath().normalize()));
        storage.rollback(committed);
        assertFalse(Files.exists(committed.path()));
    }
}
