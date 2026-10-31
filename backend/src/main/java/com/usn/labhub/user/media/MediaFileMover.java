package com.usn.labhub.user.media;

import java.io.IOException;
import java.nio.file.Path;

interface MediaFileMover {
    void moveAtomically(Path source, Path target) throws IOException;
}
