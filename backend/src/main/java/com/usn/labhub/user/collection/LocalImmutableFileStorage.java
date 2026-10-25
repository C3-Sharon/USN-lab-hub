package com.usn.labhub.user.collection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalImmutableFileStorage {
    public static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<String> MIME_TYPES = Set.of(
            "text/csv", "application/csv", "application/vnd.ms-excel", "text/plain",
            "application/octet-stream");
    private final Path root;
    private final Path staging;

    public LocalImmutableFileStorage(@Value("${usnhub.collection.storage-root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.staging = this.root.resolve(".staging");
    }

    public StagedCollectionFile stage(MultipartFile file) {
        String name = safeName(file.getOriginalFilename());
        String mime = file.getContentType() == null ? "application/octet-stream"
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv") || !MIME_TYPES.contains(mime)) {
            throw CollectionApiException.fileTypeUnsupported();
        }
        Path temp = null;
        try {
            Files.createDirectories(staging);
            temp = Files.createTempFile(staging, "upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream source = new DigestInputStream(file.getInputStream(), digest);
                 OutputStream target = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = source.read(buffer)) >= 0) {
                    size += read;
                    if (size > MAX_BYTES) {
                        throw CollectionApiException.fileTooLarge();
                    }
                    target.write(buffer, 0, read);
                }
            }
            if (size == 0) {
                throw CollectionApiException.fileTypeUnsupported();
            }
            validateUtf8AndContent(temp);
            return new StagedCollectionFile(temp, name, mime, size, HexFormat.of().formatHex(digest.digest()));
        } catch (CollectionApiException exception) {
            deleteQuietly(temp);
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            deleteQuietly(temp);
            throw CollectionApiException.fileStorageFailed();
        }
    }

    public CommittedCollectionFile commit(Long batchId, StagedCollectionFile file) {
        String objectKey = batchId + "/" + file.sha256() + ".csv";
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                deleteQuietly(file.path());
                return new CommittedCollectionFile(objectKey, target, false);
            }
            try {
                Files.move(file.path(), target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(file.path(), target);
            }
            return new CommittedCollectionFile(objectKey, target, true);
        } catch (IOException exception) {
            deleteQuietly(file.path());
            throw CollectionApiException.fileStorageFailed();
        }
    }

    public Path resolve(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw CollectionApiException.fileStorageFailed();
        }
        return resolved;
    }

    public void rollback(CommittedCollectionFile file) {
        if (file != null && file.created()) {
            deleteQuietly(file.path());
        }
    }

    public void discard(StagedCollectionFile file) {
        if (file != null) {
            deleteQuietly(file.path());
        }
    }

    private void validateUtf8AndContent(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            throw CollectionApiException.fileTypeUnsupported();
        }
        for (byte value : bytes) {
            if (value == 0) {
                throw CollectionApiException.fileTypeUnsupported();
            }
        }
        int offset = bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF ? 3 : 0;
        try {
            CharBuffer ignored = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
        } catch (CharacterCodingException exception) {
            throw CollectionApiException.fileEncodingUnsupported();
        }
    }

    private String safeName(String original) {
        if (original == null) {
            throw CollectionApiException.fileTypeUnsupported();
        }
        try {
            String name = Path.of(original).getFileName().toString();
            if (name.isBlank() || name.contains("..") || name.length() > 255) {
                throw CollectionApiException.fileTypeUnsupported();
            }
            return name;
        } catch (InvalidPathException exception) {
            throw CollectionApiException.fileTypeUnsupported();
        }
    }

    private void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Orphan cleanup is operational; never hide the original business failure.
            }
        }
    }
}
