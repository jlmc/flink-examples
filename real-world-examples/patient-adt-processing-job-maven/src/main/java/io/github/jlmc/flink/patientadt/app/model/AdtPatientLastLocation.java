package io.github.jlmc.flink.patientadt.app.model;

import java.io.Serializable;
import java.time.Instant;

public record AdtPatientLastLocation(
        String accountId,
        String patientId,
        String locationId,
        Instant lastUpdateTimestamp,
        boolean isActive,
        Instant expirationTimestamp,
        AdtEvent adtEvent) implements Serializable {

    public String patientKey() {
        return accountId + "_" + patientId;
    }
}
