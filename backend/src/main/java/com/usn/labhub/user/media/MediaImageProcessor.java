package com.usn.labhub.user.media;

import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Component
public class MediaImageProcessor {

    private static final int MAX_DIMENSION = 4096;
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final Map<String, ImageType> TYPES = Map.of(
            "jpg", new ImageType("jpg", "image/jpeg"),
            "jpeg", new ImageType("jpg", "image/jpeg"),
            "png", new ImageType("png", "image/png"));

    ProcessedImage process(Path stagedFile, MediaUploadDescriptor descriptor) {
        ImageType expected = validateDescriptor(descriptor);
        validateMagic(stagedFile, expected);

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(stagedFile.toFile())) {
            if (imageInput == null) {
                throw MediaStorageException.decodeFailed(null);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw MediaStorageException.decodeFailed(null);
            }
            ImageReader reader = readers.next();
            try {
                String readerFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!matchesReaderFormat(expected.extension(), readerFormat)) {
                    throw MediaStorageException.typeUnsupported();
                }
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw MediaStorageException.decodeFailed(null);
                }
                BufferedImage clean = copyPixels(decoded, expected.extension());
                return new ProcessedImage(expected.extension(), expected.contentType(), width, height,
                        encode(clean, expected.extension()));
            } finally {
                reader.dispose();
            }
        } catch (MediaStorageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw MediaStorageException.decodeFailed(exception);
        }
    }

    private ImageType validateDescriptor(MediaUploadDescriptor descriptor) {
        if (descriptor == null || descriptor.originalFilename() == null
                || descriptor.declaredContentType() == null) {
            throw MediaStorageException.typeUnsupported();
        }
        String filename = descriptor.originalFilename().trim();
        if (filename.isEmpty() || filename.length() > 255 || filename.contains("/")
                || filename.contains("\\") || filename.contains("..")) {
            throw MediaStorageException.typeUnsupported();
        }
        try {
            if (!Path.of(filename).getFileName().toString().equals(filename)) {
                throw MediaStorageException.typeUnsupported();
            }
        } catch (InvalidPathException exception) {
            throw MediaStorageException.typeUnsupported();
        }
        int extensionSeparator = filename.lastIndexOf('.');
        if (extensionSeparator <= 0 || extensionSeparator == filename.length() - 1) {
            throw MediaStorageException.typeUnsupported();
        }
        ImageType type = TYPES.get(filename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT));
        String declaredContentType = descriptor.declaredContentType().trim().toLowerCase(Locale.ROOT);
        if (type == null || !type.contentType().equals(declaredContentType)) {
            throw MediaStorageException.typeUnsupported();
        }
        return type;
    }

    private void validateMagic(Path stagedFile, ImageType expected) {
        byte[] header = new byte[PNG_SIGNATURE.length];
        int read;
        try (InputStream input = Files.newInputStream(stagedFile)) {
            read = input.read(header);
        } catch (IOException exception) {
            throw MediaStorageException.processingFailed(exception);
        }
        boolean matches = "png".equals(expected.extension())
                ? read >= PNG_SIGNATURE.length && Arrays.equals(header, PNG_SIGNATURE)
                : read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
        if (!matches) {
            throw MediaStorageException.typeUnsupported();
        }
    }

    private boolean matchesReaderFormat(String expected, String actual) {
        return expected.equals(actual) || ("jpg".equals(expected) && "jpeg".equals(actual));
    }

    private void validateDimensions(int width, int height) {
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw MediaStorageException.tooLarge();
        }
    }

    private BufferedImage copyPixels(BufferedImage source, String extension) {
        int type = "jpg".equals(extension)
                ? BufferedImage.TYPE_INT_RGB
                : source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encode(BufferedImage image, String format) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw MediaStorageException.processingFailed(null);
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            if (output == null) {
                throw MediaStorageException.processingFailed(null);
            }
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw MediaStorageException.processingFailed(exception);
        } finally {
            writer.dispose();
        }
    }

    private record ImageType(String extension, String contentType) {
    }
}
