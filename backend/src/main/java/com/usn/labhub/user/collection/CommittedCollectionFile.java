package com.usn.labhub.user.collection;

import java.nio.file.Path;

public record CommittedCollectionFile(String objectKey, Path path, boolean created) {
}
