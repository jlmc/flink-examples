package io.github.jlmc.flink.watermarks.outoforderness;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public class FhirAdtEvent {
    public String messageId;
    public String patientId;
    public String facilityId;
    public String eventType;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant eventTimestamp;

    public FhirAdtEvent() {
    }

    public FhirAdtEvent(String messageId, String patientId, String facilityId, String eventType, Instant eventTimestamp) {
        this.messageId = messageId;
        this.patientId = patientId;
        this.facilityId = facilityId;
        this.eventType = eventType;
        this.eventTimestamp = eventTimestamp;
    }

    @Override
    public String toString() {
        return "FhirAdtEvent{" +
                "messageId='" + messageId + '\'' +
                ", patientId='" + patientId + '\'' +
                ", facilityId='" + facilityId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", eventTimestamp=" + eventTimestamp +
                '}';
    }
}
