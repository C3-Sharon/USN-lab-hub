package com.usn.labhub.user.media;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LocalMediaStorageAdapter implements MediaStoragePort {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;
    private static final Pattern OBJECT_KEY = Pattern.compile(
            "^(original|display)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png)$");
    private static final Set<String> ALLOWED_PREFIXES = Set.of("original", "display");

    private final Path root;
    private final Path stagingDirectory;

    public LocalMediaStorageAdapter(MediaStorageProperties properties) {
        try {
            this.root = Path.of(properties.getStorageRoot()).toAbsolutePath().normalize();
            this.stagingDirectory = root.resolve("staging");
            Files.createDirectories(stagingDirectory);
            Files.createDirectories(root.resolve("original"));
            Files.createDirectories(root.resolve("display"));
        } catch (InvalidPathException | IOException exception) {
            throw MediaStorageException.storageFailed(exception);
        }
    }

    @Override
    public StagedMedia stage(InputStream source) {
        if (source == null) {
            throw MediaStorageException.typeUnsupported();
        }
        Path temporary = null;
        try {
            temporary = Files.createTempFile(stagingDirectory, "upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copyWithLimit(source, temporary, digest);
            if (size == 0) {
                throw MediaStorageException.typeUnsupported();
            }
            return new LocalStagedMedia(temporary, size, HexFormat.of().formatHex(digest.digest()));
        } catch (MediaStorageException exception) {
            deleteQuietly(temporary);
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            deleteQuietly(temporary);
            throw MediaStorageException.storageFailed(exception);
        }
    }

    @Override
    public CommittedMedia commit(StagedMedia staged, MediaUploadDescriptor descriptor) {
        if (!(staged instanceof LocalStagedMedia local) || !local.path().startsWith(stagingDirectory)) {
            throw MediaStorageException.storageFailed(null);
        }
        // Task 2 adds the validated image processing and commit pipeline.
        throw MediaStorageException.processingFailed();
    }

    @Override
    public InputStream open(String objectKey) {
        Path resolved = resolveObjectKey(objectKey);
        try {
            return Files.newInputStream(resolved);
        } catch (IOException exception) {
            throw MediaStorageException.storageFailed(exception);
        }
    }

    @Override
    public void rollbackNewObject(CommittedMedia committed) {
        throw MediaStorageException.invalidObjectKey();
    }

    private long copyWithLimit(InputStream source, Path target, MessageDigest digest) throws IOException {
        long size = 0;
        try (InputStream input = new DigestInputStream(source, digest);
             OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                size += read;
                if (size > MAX_BYTES) {
                    throw MediaStorageException.tooLarge();
                }
                output.write(buffer, 0, read);
            }
        }
        return size;
    }

    private Path resolveObjectKey(String objectKey) {
        if (objectKey == null || objectKey.contains("\\") || !OBJECT_KEY.matcher(objectKey).matches()) {
            throw MediaStorageException.invalidObjectKey();
        }
        try {
            Path logical = Path.of(objectKey);
            if (logical.isAbsolute() || logical.getNameCount() != 2
                    || !ALLOWED_PREFIXES.contains(logical.getName(0).toString())) {
                throw MediaStorageException.invalidObjectKey();
            }
            Path expectedDirectory = root.resolve(logical.getName(0).toString()).normalize();
            Path resolved = root.resolve(logical).normalize();
            if (!resolved.startsWith(expectedDirectory)) {
                throw MediaStorageException.invalidObjectKey();
            }
            return resolved;
        } catch (InvalidPathException exception) {
            throw MediaStorageException.invalidObjectKey();
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original storage error; orphan cleanup is verified separately.
        }
    }

    private record LocalStagedMedia(Path path, long sizeBytes, String sha256) implements StagedMedia {
    }
}
