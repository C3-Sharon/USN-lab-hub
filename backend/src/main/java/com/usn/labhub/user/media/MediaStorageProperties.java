package com.usn.labhub.user.media;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Data
@Component
@ConfigurationProperties(prefix = "usnhub.media")
public class MediaStorageProperties {
    private String storageRoot = Path.of(System.getProperty("user.home"), ".usn-lab-hub", "media").toString();
}
