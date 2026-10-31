package com.usn.labhub.user.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaImageProcessorTest {

    @TempDir
    Path temp;

    private final MediaImageProcessor processor = new MediaImageProcessor();

    @Test
    void validatesAndReencodesJpegAndPng() throws Exception {
        ProcessedImage jpeg = process(imageBytes("jpg", 4, 3), "board.jpeg", "image/jpeg");
        ProcessedImage png = process(imageBytes("png", 5, 2), "module.png", "image/png");

        assertEquals("jpg", jpeg.extension());
        assertEquals("image/jpeg", jpeg.contentType());
        assertEquals(4, jpeg.width());
        assertEquals(3, jpeg.height());
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(jpeg.displayBytes())));

        assertEquals("png", png.extension());
        assertEquals("image/png", png.contentType());
        assertEquals(5, png.width());
        assertEquals(2, png.height());
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(png.displayBytes())));
    }

    @Test
    void rejectsDescriptorMagicAndUnsupportedFormatMismatches() throws Exception {
        byte[] png = imageBytes("png", 2, 2);
        byte[] gif = imageBytes("gif", 2, 2);
        List<InvalidSample> samples = List.of(
                new InvalidSample(png, "board.jpg", "image/jpeg"),
                new InvalidSample(png, "board.png", "image/jpeg"),
                new InvalidSample(gif, "board.gif", "image/gif"),
                new InvalidSample("<svg><script/></svg>".getBytes(StandardCharsets.UTF_8), "board.svg", "image/svg+xml"),
                new InvalidSample("RIFFxxxxWEBP".getBytes(StandardCharsets.US_ASCII), "board.webp", "image/webp"),
                new InvalidSample("alert(1)".getBytes(StandardCharsets.UTF_8), "board.jpg", "image/jpeg"));

        for (InvalidSample sample : samples) {
            Path file = Files.createTempFile(temp, "invalid-", ".bin");
            Files.write(file, sample.bytes());
            MediaStorageException exception = assertThrows(MediaStorageException.class,
                    () -> processor.process(file,
                            new MediaUploadDescriptor(sample.filename(), sample.contentType())));
            assertEquals(MediaStorageException.Reason.TYPE_UNSUPPORTED, exception.getReason(), sample.filename());
        }
    }

    @Test
    void rejectsUnsafeNamesDamagedImagesAndOversizedDimensions() throws Exception {
        byte[] jpeg = imageBytes("jpg", 2, 2);
        Path valid = Files.createTempFile(temp, "valid-", ".jpg");
        Files.write(valid, jpeg);
        for (String name : List.of("", "../board.jpg", "folder/board.jpg", "folder\\board.jpg",
                "board..jpg", "board.exe", "a".repeat(252) + ".jpg", "bad\0name.jpg")) {
            MediaStorageException exception = assertThrows(MediaStorageException.class,
                    () -> processor.process(valid, new MediaUploadDescriptor(name, "image/jpeg")));
            assertEquals(MediaStorageException.Reason.TYPE_UNSUPPORTED, exception.getReason(), name);
        }

        Path damaged = Files.createTempFile(temp, "damaged-", ".jpg");
        Files.write(damaged, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01});
        MediaStorageException decode = assertThrows(MediaStorageException.class,
                () -> processor.process(damaged, new MediaUploadDescriptor("board.jpg", "image/jpeg")));
        assertEquals(MediaStorageException.Reason.DECODE_FAILED, decode.getReason());

        Path oversized = Files.createTempFile(temp, "oversized-", ".png");
        Files.write(oversized, imageBytes("png", 4097, 1));
        MediaStorageException dimensions = assertThrows(MediaStorageException.class,
                () -> processor.process(oversized, new MediaUploadDescriptor("wide.png", "image/png")));
        assertEquals(MediaStorageException.Reason.TYPE_UNSUPPORTED, dimensions.getReason());
    }

    @Test
    void reencodingDoesNotCopyTrailingSourceMetadata() throws Exception {
        String marker = "USN_PRIVATE_SOURCE_COMMENT";
        byte[] source = imageBytes("png", 3, 3);
        ByteArrayOutputStream withMetadata = new ByteArrayOutputStream();
        withMetadata.write(source);
        withMetadata.write(marker.getBytes(StandardCharsets.US_ASCII));

        ProcessedImage processed = process(withMetadata.toByteArray(), "board.png", "image/png");

        assertFalse(new String(processed.displayBytes(), StandardCharsets.ISO_8859_1).contains(marker));
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(processed.displayBytes())));
    }

    private ProcessedImage process(byte[] bytes, String filename, String contentType) throws Exception {
        Path file = Files.createTempFile(temp, "image-", ".bin");
        Files.write(file, bytes);
        return processor.process(file, new MediaUploadDescriptor(filename, contentType));
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

    private record InvalidSample(byte[] bytes, String filename, String contentType) {
    }
}
