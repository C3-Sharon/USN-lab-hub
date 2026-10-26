package com.usn.labhub.user.collection;

import java.nio.file.Path;

public record StagedCollectionFile(Path path, String originalFileName, String mimeType,
                                   long size, String sha256) {
}
