# B4 Local Media Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved B4 supplier-neutral media storage port and a secure local single-instance JPEG/PNG adapter without exposing a business HTTP API.

**Architecture:** Keep media persistence behind `MediaStoragePort`. The local adapter streams uploads into a staging directory, computes immutable original metadata, asks a JDK `ImageIO` processor to validate and re-encode a safe display copy, then atomically moves the original and display files under random logical object keys. A package-private local commit handle is the only value accepted by compensation, preventing callers from asking the adapter to delete arbitrary paths.

**Tech Stack:** Java 17, Spring Boot 3.1.5, JDK NIO, JDK `ImageIO`, JUnit 5, Spring test utilities, Maven.

## Global Constraints

- Follow `docs/contracts/COMPONENT_CATALOG_MEDIA_API.md` v1.0.4 and the approved B4 section in `docs/backend/execution/2026-W44.md`.
- Do not add an HTTP API, database mapper, Flyway migration, permission rule, object-storage SDK, image library, asynchronous cleanup job, or thumbnail feature in B4.
- Accept only JPEG (`.jpg`/`.jpeg`, `image/jpeg`) and PNG (`.png`, `image/png`), at most 5 MiB, with width and height each in `1..4096`.
- Preserve original bytes. Generate the display file from a new `BufferedImage` and never copy source metadata.
- Keep physical paths internal. Public internal-domain values expose logical keys and immutable metadata only.
- Use `${USNHUB_MEDIA_ROOT:${user.home}/.usn-lab-hub/media}`; never write uploaded media into the repository or build output.
- Reject path traversal, absolute paths, unknown key prefixes, inconsistent extension/MIME/magic, damaged images, and unsupported formats.
- Use fixed deterministic samples and injected I/O failures. Do not add unbounded randomized tests or repeatedly run the full backend suite.
- B4 exceptions remain internal. Mapping to frozen `MEDIA_*` API reasons belongs to B5.

---

### Task 1: Define The Port And Safe Staging Boundary

**Files:**
- Create: `backend/src/main/java/com/usn/labhub/user/media/MediaStoragePort.java`
- Create: `backend/src/main/java/com/usn/labhub/user/media/MediaUploadDescriptor.java`
- Create: `backend/src/main/java/com/usn/labhub/user/media/StagedMedia.java`
- Create: `backend/src/main/java/com/usn/labhub/user/media/CommittedMedia.java`
- Create: `backend/src/main/java/com/usn/labhub/user/media/MediaStorageException.java`
- Create: `backend/src/main/java/com/usn/labhub/user/media/MediaStorageProperties.java`
- Create: `backend/src/main/java/com/usn/labhub/user/media/LocalMediaStorageAdapter.java`
- Create: `backend/src/test/java/com/usn/labhub/user/media/LocalMediaStorageAdapterTest.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**

```java
public interface MediaStoragePort {
    StagedMedia stage(InputStream source);
    CommittedMedia commit(StagedMedia staged, MediaUploadDescriptor descriptor);
    InputStream open(String objectKey);
    void rollbackNewObject(CommittedMedia committed);
}

public record MediaUploadDescriptor(String originalFilename, String declaredContentType) {}

public interface StagedMedia {
    long sizeBytes();
    String sha256();
}

public interface CommittedMedia {
    String originalObjectKey();
    String displayObjectKey();
    long sizeBytes();
    String sha256();
    String contentType();
    int width();
    int height();
}
```

The adapter returns package-private local implementations of `StagedMedia` and `CommittedMedia`. It must reject foreign implementations in `commit` and `rollbackNewObject`.

- [x] Add failing tests for a 5 MiB boundary success, a `5 MiB + 1 byte` failure, SHA-256 calculation, empty input rejection, and staging cleanup after failure.
- [x] Add failing tests showing `open` rejects `../`, absolute paths, backslashes, `staging/`, and unknown prefixes before touching the filesystem.
- [x] Configure `usnhub.media.storage-root: ${USNHUB_MEDIA_ROOT:${user.home}/.usn-lab-hub/media}` in `application.yml`.
- [x] Implement `MediaStorageProperties` using the repository's existing `@Component` plus `@ConfigurationProperties` style.
- [x] Implement streaming staging with an 8 KiB buffer, incremental SHA-256, immediate size-limit abort, and cleanup on every failure path.
- [x] Normalize the configured root once and create only `staging`, `original`, and `display` beneath it.
- [x] Implement controlled logical-key resolution: only `original/<uuid>.<ext>` and `display/<uuid>.<ext>` are readable, and the normalized resolved path must remain under its expected directory.
- [x] Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=LocalMediaStorageAdapterTest test
```

Expected: staging and path-boundary tests pass; image commit tests may remain pending until Tasks 2-3.

- [x] Commit:

```text
feat: define safe local media staging port
```

---

### Task 2: Validate And Re-Encode JPEG/PNG

**Files:**
- Create: `backend/src/main/java/com/usn/labhub/user/media/MediaImageProcessor.java`
- Create: `backend/src/main/java/com/usn/labhub/user/media/ProcessedImage.java`
- Create: `backend/src/test/java/com/usn/labhub/user/media/MediaImageProcessorTest.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/media/LocalMediaStorageAdapter.java`
- Modify: `backend/src/test/java/com/usn/labhub/user/media/LocalMediaStorageAdapterTest.java`

**Processing contract:**

```java
interface MediaImageProcessor {
    ProcessedImage process(Path stagedFile, MediaUploadDescriptor descriptor);
}

record ProcessedImage(
        String extension,
        String contentType,
        int width,
        int height,
        byte[] displayBytes) {}
```

Validation order is fixed: staged size already verified, then safe basename/extension, declared MIME, magic bytes, dimensions from an `ImageReader`, full decode, and re-encode.

- [x] Add deterministic in-memory JPEG and PNG fixtures generated from a small known `BufferedImage`; do not commit binary sample files.
- [x] Add failing tests for valid JPEG/PNG, `.jpeg` normalization, extension/MIME mismatch, magic mismatch, GIF/SVG/WebP/script input, damaged/truncated data, empty data, and dimensions outside `1..4096`.
- [x] Add a metadata-removal test by writing a fixed source image with a harmless comment metadata node where supported, processing it, and asserting the decoded display image is valid while the comment is absent. If the JDK writer does not support the fixture metadata, assert instead that the display bytes were produced from a newly created image and differ from a source carrying fixed trailing metadata bytes.
- [x] Validate the original filename as a basename only: reject blank, more than 255 characters, separators, `..`, invalid path syntax, and unsupported extensions.
- [x] Detect JPEG by `FF D8 FF` and PNG by the eight-byte PNG signature; require exact agreement with normalized extension and declared MIME.
- [x] Read dimensions before full raster allocation, reject invalid bounds, then fully decode. Copy pixels into a fresh image (`TYPE_INT_RGB` for JPEG, alpha-capable type for PNG).
- [x] Re-encode with a selected `ImageWriter`; fail closed if no reader/writer exists or decode/encode returns no image.
- [x] Keep all exceptions in a small internal reason enum such as `TOO_LARGE`, `TYPE_UNSUPPORTED`, `DECODE_FAILED`, `PROCESSING_FAILED`, `STORAGE_FAILED`, and `INVALID_OBJECT_KEY`.
- [x] Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=MediaImageProcessorTest,LocalMediaStorageAdapterTest test
```

Expected: every supported/unsupported image class has one direct deterministic test and no source path or filename appears in returned metadata.

- [x] Commit:

```text
feat: validate and re-encode media images
```

---

### Task 3: Atomic Commit, Compensation, Startup Cleanup, And B4 Evidence

**Files:**
- Create: `backend/src/main/java/com/usn/labhub/user/media/MediaFileMover.java`
- Create: `backend/src/main/java/com/usn/labhub/user/media/NioMediaFileMover.java`
- Modify: `backend/src/main/java/com/usn/labhub/user/media/LocalMediaStorageAdapter.java`
- Modify: `backend/src/test/java/com/usn/labhub/user/media/LocalMediaStorageAdapterTest.java`
- Modify: `docs/backend/execution/2026-W44.md`

**Failure boundary:**

```java
interface MediaFileMover {
    void moveAtomically(Path source, Path target) throws IOException;
}
```

`NioMediaFileMover` uses `Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)`. Do not silently downgrade to a non-atomic move. An unsupported atomic move is a storage failure.

- [x] Add failing tests for a successful original/display commit and byte-for-byte original round trip through `open`.
- [x] Add a fixed mover failure on the first move and assert both final directories and staging are clean.
- [x] Add a fixed mover failure on the second move and assert the first committed file is deleted and staging is clean.
- [x] Add a rollback test proving only the two files from the returned local commit handle are removed.
- [x] Add tests proving a fabricated/foreign `CommittedMedia` cannot trigger deletion and repeated rollback is harmless.
- [x] Add a startup cleanup test: stale regular files under `staging` are removed, while `original` and `display` are untouched. Treat cleanup failure as startup failure rather than silently running with an uncertain staging state.
- [x] Generate one UUID per physical object key, use normalized lowercase extensions, and create target directories before moving.
- [x] Write the display output to a staging file first. Move original, then display. On the second failure, delete the newly moved original and all staging files created by this operation.
- [x] Return a package-private local commit handle containing controlled logical keys and immutable metadata. `rollbackNewObject` must type-check it and resolve both keys through the same controlled resolver.
- [x] Perform B4-only verification once:

```powershell
cd backend
.\mvnw.cmd -Dtest=MediaImageProcessorTest,LocalMediaStorageAdapterTest test
.\mvnw.cmd -DskipTests compile
```

Expected: focused tests pass and main compilation succeeds. Do not run the full backend suite in B4; it is reserved for B6 after B5 changes.

- [x] Manually inspect the configured test directory and confirm no upload, staging file, absolute path, or generated binary was added to Git.
- [x] Update `docs/backend/execution/2026-W44.md`: mark B4 `VERIFIED`, record exact test counts/results, list the actual storage and compensation behavior, update the current checkpoint to the B5 pause gate, and leave B5/B6 as not completed.
- [x] Review `git diff --check`, changed files, configuration defaults, and dependency diff. Confirm `backend/pom.xml` is unchanged.
- [x] Commit and push:

```text
feat: complete local media storage compensation
```

### B4 Exit Gate

- [x] Legal JPEG/PNG files can be staged, validated, committed, opened, and rolled back using logical keys only.
- [x] Original bytes and SHA-256 remain unchanged; display bytes are newly encoded without copied source metadata.
- [x] Every invalid class is rejected before final commit and leaves no staging/final residue.
- [x] A second-move failure removes the first new object; rollback cannot target arbitrary or pre-existing files.
- [x] No API, database, permission, MQTT, cache, asynchronous, or dependency change was introduced.
- [x] Stop and report the B4 evidence. Do not enter B5 until the user confirms the file/database compensation boundary again.

