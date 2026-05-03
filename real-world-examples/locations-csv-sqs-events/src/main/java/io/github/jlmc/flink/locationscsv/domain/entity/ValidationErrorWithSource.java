package io.github.jlmc.flink.locationscsv.domain.entity;

import java.io.Serializable;

public record ValidationErrorWithSource(
        String sourceFilePath,
        long line,
        String lineContent,
        String error,
        long timestamp
) implements Serializable {
}
