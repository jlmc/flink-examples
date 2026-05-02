package io.github.jlmc.flink.locationscsv.source;

import java.io.Serializable;

public record S3ObjectEvent(
        String bucket,
        String key,
        String eventName
) implements Serializable {
}
