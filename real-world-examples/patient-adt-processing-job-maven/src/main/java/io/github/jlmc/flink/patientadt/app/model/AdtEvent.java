package io.github.jlmc.flink.patientadt.app.model;

import java.io.Serializable;
import java.time.Instant;

public class AdtEvent implements Serializable {

    public String accountId;
    public String patientId;
    public String eventType;
    public String locationId;
    public Instant eventTimestamp;

    public AdtEvent() {
    }

    public String patientKey() {
        return accountId + "_" + patientId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getLocationId() {
        return locationId;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }
}
