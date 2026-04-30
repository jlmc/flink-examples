package io.github.jlmc.flink.patientadt.model;

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
}
