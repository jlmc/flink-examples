package io.github.jlmc.flink.locationscsv.domain.entity;

import java.io.Serializable;

public record FileProcessingMetric(
        String sourceFilePath,
        MetricType metricType,
        long line,
        String error
) implements Serializable {
    public enum MetricType {
        VALID_ROW,
        INVALID_ROW,
        FILE_COMPLETED
    }
}
