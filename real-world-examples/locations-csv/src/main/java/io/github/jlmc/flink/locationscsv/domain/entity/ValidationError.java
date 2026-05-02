package io.github.jlmc.flink.locationscsv.domain.entity;

import java.io.Serializable;

public record ValidationError(
        long lineNumber,
        String lineContent,
        String reason,
        long timestamp
) implements Serializable {
}
