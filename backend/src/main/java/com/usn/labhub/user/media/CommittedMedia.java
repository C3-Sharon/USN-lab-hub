package com.usn.labhub.user.media;

public interface CommittedMedia {
    String originalObjectKey();
    String displayObjectKey();
    long sizeBytes();
    String sha256();
    String contentType();
    int width();
    int height();
}
