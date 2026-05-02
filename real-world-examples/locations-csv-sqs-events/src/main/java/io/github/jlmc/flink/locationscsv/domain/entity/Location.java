package io.github.jlmc.flink.locationscsv.domain.entity;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

@JsonPropertyOrder({"name", "lat", "lon", "imgUrl"}) // Defines CSV column order
public record Location(
    String name,
    Double lat,
    Double lon,
    String imgUrl
) implements Serializable {
}
