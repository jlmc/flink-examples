package io.github.jlmc.flink.patientadt.components.statefull;

import io.github.jlmc.flink.patientadt.app.services.AdtPatientLastLocationResolver;
import io.github.jlmc.flink.patientadt.app.model.AdtEvent;
import io.github.jlmc.flink.patientadt.app.model.AdtPatientLastLocation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtPatientLastLocationResolverTest {

    private static final Duration DISCHARGED_TTL = Duration.ofHours(2);

    @Test
    void shouldReturnNullWhenEventsAreNull() {
        AdtPatientLastLocationResolver resolver = resolverAt("2026-01-01T00:00:00Z");

        AdtPatientLastLocation result = resolver.resolveLatestValid(null, DISCHARGED_TTL);

        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenEventsAreEmpty() {
        AdtPatientLastLocationResolver resolver = resolverAt("2026-01-01T00:00:00Z");

        AdtPatientLastLocation result = resolver.resolveLatestValid(List.of(), DISCHARGED_TTL);

        assertNull(result);
    }

    @Test
    void shouldResolveLatestActiveLocationUsingChronologicalOrder() {
        AdtPatientLastLocationResolver resolver = resolverAt("2026-01-01T10:00:00Z");

        AdtEvent transfer = event("A1", "P1", "A02", "WARD-B", "2026-01-01T09:00:00Z");
        AdtEvent admit = event("A1", "P1", "A01", "WARD-A", "2026-01-01T08:00:00Z");

        AdtPatientLastLocation result = resolver.resolveLatestValid(List.of(transfer, admit), DISCHARGED_TTL);

        assertNotNull(result);
        assertTrue(result.isActive());
        assertEquals("WARD-B", result.locationId());
        assertEquals(Instant.parse("2026-01-01T09:00:00Z"), result.lastUpdateTimestamp());
        assertNull(result.expirationTimestamp());
    }

    @Test
    void shouldDiscardCancelledEventPair() {
        AdtPatientLastLocationResolver resolver = resolverAt("2026-01-01T10:00:00Z");

        AdtEvent admit = event("A1", "P1", "A01", "WARD-A", "2026-01-01T08:00:00Z");
        AdtEvent cancelAdmit = event("A1", "P1", "A11", "WARD-A", "2026-01-01T09:00:00Z");

        AdtPatientLastLocation result = resolver.resolveLatestValid(List.of(admit, cancelAdmit), DISCHARGED_TTL);

        assertNull(result);
    }

    @Test
    void shouldReturnInactiveLocationAndExpirationWhenWithinTtl() {
        AdtPatientLastLocationResolver resolver = resolverAt("2026-01-01T10:00:00Z");

        AdtEvent discharge = event("A1", "P1", "A03", "WARD-A", "2026-01-01T09:00:00Z");

        AdtPatientLastLocation result = resolver.resolveLatestValid(List.of(discharge), DISCHARGED_TTL);

        assertNotNull(result);
        assertFalse(result.isActive());
        assertEquals(Instant.parse("2026-01-01T11:00:00Z"), result.expirationTimestamp());
    }

    @Test
    void shouldReturnNullWhenInactiveLocationIsExpired() {
        AdtPatientLastLocationResolver resolver = resolverAt("2026-01-01T13:00:01Z");

        AdtEvent discharge = event("A1", "P1", "A03", "WARD-A", "2026-01-01T09:00:00Z");

        AdtPatientLastLocation result = resolver.resolveLatestValid(List.of(discharge), DISCHARGED_TTL);

        assertNull(result);
    }

    private static AdtPatientLastLocationResolver resolverAt(String nowIso) {
        Clock fixedClock = Clock.fixed(Instant.parse(nowIso), ZoneOffset.UTC);
        return new AdtPatientLastLocationResolver(fixedClock);
    }

    private static AdtEvent event(String accountId, String patientId, String eventType, String locationId, String eventTimestamp) {
        AdtEvent event = new AdtEvent();
        event.accountId = accountId;
        event.patientId = patientId;
        event.eventType = eventType;
        event.locationId = locationId;
        event.eventTimestamp = Instant.parse(eventTimestamp);
        return event;
    }
}
