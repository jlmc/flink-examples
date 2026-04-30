package io.github.jlmc.flink.watermarks.outoforderness;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public final class AdtWindowResult implements java.io.Serializable {
    public String facilityId;
    public String eventType;
    public long totalEvents;
    public long admits;
    public long discharges;
    public long transfers;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant start;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant end;

    public AdtWindowResult() {
    }

    public AdtWindowResult(String facilityId,
                           String eventType,
                           long totalEvents,
                           long admits,
                           long discharges,
                           long transfers,
                           Instant start,
                           Instant end) {
        this.facilityId = facilityId;
        this.eventType = eventType;
        this.totalEvents = totalEvents;
        this.admits = admits;
        this.discharges = discharges;
        this.transfers = transfers;
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return String.format("Facility: %s | EventType: %s | Total: %d (A01=%d, A03=%d, A02=%d) | Window: [%s - %s]",
                facilityId, eventType, totalEvents, admits, discharges, transfers, start, end);
    }
}
