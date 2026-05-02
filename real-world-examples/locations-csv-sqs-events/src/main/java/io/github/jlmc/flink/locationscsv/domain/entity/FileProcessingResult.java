package io.github.jlmc.flink.locationscsv.domain.entity;

import java.io.Serializable;
import java.util.List;

public record FileProcessingResult(
        String sourceFilePath,
        String result,
        String message,
        List<FileProcessingError> errors
) implements Serializable {
}
