package com.usn.labhub.user.media;

import java.io.InputStream;

public interface MediaStoragePort {
    StagedMedia stage(InputStream source);
    CommittedMedia commit(StagedMedia staged, MediaUploadDescriptor descriptor);
    InputStream open(String objectKey);
    void rollbackNewObject(CommittedMedia committed);
}
