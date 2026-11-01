package com.usn.labhub.user.media;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
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
    private final MediaImageProcessor imageProcessor;
    private final MediaFileMover fileMover;

    @Autowired
    public LocalMediaStorageAdapter(
            MediaStorageProperties properties,
            MediaImageProcessor imageProcessor,
            MediaFileMover fileMover) {
        try {
            this.root = Path.of(properties.getStorageRoot()).toAbsolutePath().normalize();
            this.stagingDirectory = root.resolve("staging");
            this.imageProcessor = imageProcessor;
            this.fileMover = fileMover;
            Files.createDirectories(stagingDirectory);
            Files.createDirectories(root.resolve("original"));
            Files.createDirectories(root.resolve("display"));
            clearStagingOnStartup();
        } catch (InvalidPathException | IOException exception) {
            throw MediaStorageException.storageFailed(exception);
        }
    }

    LocalMediaStorageAdapter(MediaStorageProperties properties) {
        this(properties, new MediaImageProcessor(), new NioMediaFileMover());
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
        LocalStagedMedia local = requireLocalStagedMedia(staged);
        Path displayStaging = null;
        Path originalTarget = null;
        boolean originalMoved = false;
        try {
            ProcessedImage processed = imageProcessor.process(local.path(), descriptor);
            displayStaging = Files.createTempFile(stagingDirectory, "display-", ".tmp");
            Files.write(displayStaging, processed.displayBytes());

            String originalKey = objectKey("original", processed.extension());
            String displayKey = objectKey("display", processed.extension());
            originalTarget = resolveObjectKey(originalKey);
            Path displayTarget = resolveObjectKey(displayKey);

            fileMover.moveAtomically(local.path(), originalTarget);
            originalMoved = true;
            fileMover.moveAtomically(displayStaging, displayTarget);
            return new LocalCommittedMedia(root, originalKey, displayKey, local.sizeBytes(), local.sha256(),
                    processed.contentType(), processed.width(), processed.height());
        } catch (MediaStorageException exception) {
            cleanupFailedCommit(local.path(), displayStaging, originalMoved ? originalTarget : null);
            throw exception;
        } catch (IOException exception) {
            cleanupFailedCommit(local.path(), displayStaging, originalMoved ? originalTarget : null);
            throw MediaStorageException.storageFailed(exception);
        }
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
        if (!(committed instanceof LocalCommittedMedia local) || !root.equals(local.ownerRoot())) {
            throw MediaStorageException.invalidObjectKey();
        }
        deleteQuietly(resolveObjectKey(local.originalObjectKey()));
        deleteQuietly(resolveObjectKey(local.displayObjectKey()));
    }

    private LocalStagedMedia requireLocalStagedMedia(StagedMedia staged) {
        if (!(staged instanceof LocalStagedMedia local)
                || !stagingDirectory.equals(local.path().getParent())
                || !Files.isRegularFile(local.path(), LinkOption.NOFOLLOW_LINKS)) {
            throw MediaStorageException.storageFailed(null);
        }
        return local;
    }

    private String objectKey(String prefix, String extension) {
        return prefix + "/" + UUID.randomUUID() + "." + extension;
    }

    private void cleanupFailedCommit(Path originalStaging, Path displayStaging, Path movedOriginal) {
        deleteQuietly(originalStaging);
        deleteQuietly(displayStaging);
        deleteQuietly(movedOriginal);
    }

    private void clearStagingOnStartup() throws IOException {
        try (var entries = Files.list(stagingDirectory)) {
            for (Path entry : entries.toList()) {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unexpected non-file entry in media staging directory");
                }
                Files.delete(entry);
            }
        }
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

    private record LocalCommittedMedia(
            Path ownerRoot,
            String originalObjectKey,
            String displayObjectKey,
            long sizeBytes,
            String sha256,
            String contentType,
            int width,
            int height) implements CommittedMedia {
    }
}
