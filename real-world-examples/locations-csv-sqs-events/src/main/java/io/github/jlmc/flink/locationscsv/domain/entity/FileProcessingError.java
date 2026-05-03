package io.github.jlmc.flink.locationscsv.domain.entity;

import java.io.Serializable;

public record FileProcessingError(long line, String error) implements Serializable {
}
