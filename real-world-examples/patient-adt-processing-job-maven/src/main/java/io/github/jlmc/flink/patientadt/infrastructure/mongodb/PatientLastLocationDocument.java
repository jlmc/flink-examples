package io.github.jlmc.flink.patientadt.infrastructure.mongodb;

import io.github.jlmc.flink.patientadt.app.model.AdtPatientLastLocation;
import org.bson.Document;

import java.time.Instant;

public record PatientLastLocationDocument(
        String id,
        String patientId,
        String accountId,
        String locationId,
        Instant lastUpdateTimestamp,
        boolean isActive,
        Instant expirationTimestamp,
        Instant createdAt,
        Instant updatedAt
) {

    public static PatientLastLocationDocument from(AdtPatientLastLocation event) {
        final Instant now = Instant.now();
        final Instant createdAt = event.lastUpdateTimestamp() != null ? event.lastUpdateTimestamp() : now;

        return new PatientLastLocationDocument(
                event.patientKey(),
                event.patientId(),
                event.accountId(),
                event.locationId(),
                event.lastUpdateTimestamp(),
                event.isActive(),
                event.expirationTimestamp(),
                createdAt,
                now
        );
    }

    public Document toDocument() {
        return new Document()
                .append("_id", id)
                .append("patientId", patientId)
                .append("accountId", accountId)
                .append("locationId", locationId)
                .append("lastUpdateTimestamp", lastUpdateTimestamp)
                .append("isActive", isActive)
                .append("expirationTimestamp", expirationTimestamp)
                .append("createdAt", createdAt)
                .append("updatedAt", updatedAt);
    }
}
