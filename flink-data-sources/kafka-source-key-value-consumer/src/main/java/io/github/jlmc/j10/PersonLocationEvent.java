package io.github.jlmc.j10;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public record PersonLocationEvent(
        @JsonProperty("person_id")
        String personId,
        @JsonProperty("latitude")
        double latitude,
        @JsonProperty("longitude")
        double longitude,
        @JsonProperty("event_timestamp")
        long eventTimestamp
) {
}
