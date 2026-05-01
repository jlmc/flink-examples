package io.github.jlmc.flink.patientadt.app.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum AdtEventType {

    // --- ADMISSÃO, REGISTRO E PRÉ-ADMISSÃO ---
    A01("Admit/visit notification"),
    A04("Register a patient"),
    A05("Pre-admit a patient"),
    A10("Patient arriving - tracking"),
    A14("Pending admit"),
    A16("Pending discharge"),

    // --- TRANSFERÊNCIAS E MOVIMENTAÇÃO ---
    A02("Transfer a patient"),
    A08("Update patient information"),
    A09("Patient departing - tracking"),
    A15("Pending transfer"),
    A25("Check-in patient at intermediate location"),
    A26("Check-out patient from intermediate location"),

    // --- ALTA E ENCERRAMENTO ---
    A03("Discharge/end visit"),
    A06("Change outpatient to inpatient (Inpatient Tracking)"),
    A07("Change inpatient to outpatient"),
    A39("Case awareness - patient is deceased"), // Óbito costuma encerrar localização

    // --- AUSÊNCIAS TEMPORÁRIAS (LOA - Leave of Absence) ---
    A21("Leave of absence out (Patient leaves hospital briefly)"),
    A22("Leave of absence in (Patient returns)"),
    A52("Cancel leave of absence in", "A22"),
    A53("Cancel leave of absence out", "A21"),

    // --- CANCELAMENTOS (UNDO) ---
    A11("Cancel admit/visit notification", "A01"),
    A12("Cancel transfer", "A02"),
    A13("Cancel discharge/end visit", "A03"),
    A27("Cancel pending transfer", "A15"),
    A38("Cancel pre-admit", "A05"),
    A42("Cancel prior to arrival (Cancel A10)", "A10"),

    // --- MANUTENÇÃO DE DADOS E PESSOA ---
    A28("Add person information"),
    A31("Update person information"),
    A40("Merge patient - patient identifier list"),
    A47("Change patient identifier list"),

    UNKNOWN("Unknown event type");

    private final String description;
    private final String cancelsEvent;

    /**
     * Eventos que indicam presença física ou ativação de localização.
     */
    private static final Set<AdtEventType> ACTIVE_LOCATION_EVENT_TYPES = EnumSet.of(
            A01,
            A02,
            A04,
            A05,
            A08,
            A10,
            A22
    );

    /**
     * Eventos que indicam saída ou encerramento.
     */
    private static final Set<AdtEventType> INACTIVE_LOCATION_EVENT_TYPES = EnumSet.of(
            A03,
            A21
    );

    AdtEventType(String description) {
        this(description, null);
    }

    AdtEventType(String description, String cancelsEvent) {
        this.description = description;
        this.cancelsEvent = cancelsEvent;
    }

    public boolean isCancellation() {
        return cancelsEvent != null;
    }

    public boolean isLocationActivationEvent() {
        return ACTIVE_LOCATION_EVENT_TYPES.contains(this);
    }

    public boolean isLocationDeactivationEvent() {
        return INACTIVE_LOCATION_EVENT_TYPES.contains(this);
    }

    public String getCancelsEvent() {
        return cancelsEvent;
    }

    private static final Map<String, AdtEventType> CACHE = Arrays.stream(AdtEventType.values())
            .collect(Collectors.toMap(AdtEventType::name, e -> e));


    public static AdtEventType fromCode(String code) {
        if (code == null) return UNKNOWN;
        return CACHE.getOrDefault(code.trim().toUpperCase(), UNKNOWN);
    }

    public AdtEventType getTargetType() {
        return fromCode(cancelsEvent);
    }
}
