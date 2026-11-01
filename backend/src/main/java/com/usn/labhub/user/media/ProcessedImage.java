package com.usn.labhub.user.media;

record ProcessedImage(
        String extension,
        String contentType,
        int width,
        int height,
        byte[] displayBytes) {
}
