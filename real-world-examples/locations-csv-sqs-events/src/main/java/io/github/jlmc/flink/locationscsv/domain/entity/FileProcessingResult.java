package io.github.jlmc.flink.locationscsv.domain.entity;

import java.io.Serializable;

public record FileProcessingResult(
        String sourceFilePath,
        String result,
        String message,
        FileProcessingError[] errors
) implements Serializable {
}
