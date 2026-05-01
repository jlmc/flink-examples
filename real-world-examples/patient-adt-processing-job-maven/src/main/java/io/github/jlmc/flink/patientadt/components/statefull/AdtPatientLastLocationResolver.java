package io.github.jlmc.flink.patientadt.components.statefull;

import io.github.jlmc.flink.patientadt.model.AdtEvent;
import io.github.jlmc.flink.patientadt.model.AdtEventType;
import io.github.jlmc.flink.patientadt.model.AdtPatientLastLocation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class AdtPatientLastLocationResolver {
    private static final Comparator<AdtEvent> COMPARING = Comparator.comparing(AdtEvent::getEventTimestamp);

    private final Clock clock;

    public AdtPatientLastLocationResolver() {
        this(Clock.systemUTC());
    }

    public AdtPatientLastLocationResolver(Clock clock) {
        this.clock = clock;
    }

    AdtPatientLastLocation resolveLatestValid(
            Collection<AdtEvent> events,
            Duration dischargedTtl
    ) {
        if (events == null || events.isEmpty()) {
            return null;
        }

        // 1. Ordenação cronológica (Essencial para o pareamento de cancelamentos)
        List<AdtEvent> sortedEvents = new LinkedList<>(events);
        sortedEvents.sort(COMPARING);


        // 2. Limpeza do histórico: Remove os pares (Evento Original + Evento de Cancelamento)
        List<AdtEvent> effectiveEvents = applyCancellations(sortedEvents);

        if (effectiveEvents.isEmpty()) {
            return null;
        }

        // 3. Processamento do saldo final de eventos
        final LatestLocationState latestLocationState = processFinalEventBalance(effectiveEvents);
        final AdtEvent latestLocationEvent = latestLocationState.latestLocationEvent();
        final boolean isActive = latestLocationState.isActive();

        if (latestLocationEvent == null) {
            return null;
        }

        // 4. Verificação de TTL (Invalida a localização se a alta foi há muito tempo)
        final Instant expiration = calculateExpirationTimestamp(latestLocationEvent, isActive, dischargedTtl);
        if (isExpired(expiration)) {
            return null;
        }

        return new AdtPatientLastLocation(
                latestLocationEvent.getAccountId(),
                latestLocationEvent.getPatientId(),
                latestLocationEvent.getLocationId(),
                latestLocationEvent.getEventTimestamp(),
                isActive,
                expiration,
                latestLocationEvent
        );
    }

    private List<AdtEvent> applyCancellations(List<AdtEvent> sortedEvents) {
        LinkedList<AdtEvent> result = new LinkedList<>(sortedEvents);

        for (int i = result.size() - 1; i >= 0; i--) {
            AdtEvent adtEvent = result.get(i);

            var eventType =
                    Optional.ofNullable(adtEvent.getEventType())
                            .map(AdtEventType::fromCode)
                            .orElse(null);

            if (eventType == null) {
                continue;
            }

            if (eventType.isCancellation()) {
                int cancelIndex = i;

                AdtEventType targetType = eventType.getTargetType();

                // we must search the original event witch this cancellation undo.
                for (int j = cancelIndex - 1; j >= 0; j--) {

                    AdtEventType candidateType = AdtEventType.fromCode(result.get(j).getEventType());

                    if (candidateType == targetType) {
                        // Encontrou o par. Remove ambos (o cancelador e o original)
                        result.remove(cancelIndex);
                        result.remove(j);

                        // Reset do ponteiro para re-avaliar a lista após mutação
                        i = result.size();
                        break;
                    }
                }
            }
        }

        return result;
    }

    private LatestLocationState processFinalEventBalance(List<AdtEvent> effectiveEvents) {
        AdtEvent latestLocationEvent = null;
        boolean isActive = false;

        for (AdtEvent event : effectiveEvents) {
            AdtEventType type = AdtEventType.fromCode(event.getEventType());

            // Eventos que indicam presença física ou ativação de localização
            if (type.isLocationActivationEvent()) {
                latestLocationEvent = event;
                isActive = true;
            } else if (type.isLocationDeactivationEvent()) {
                // Eventos que indicam saída ou encerramento
                latestLocationEvent = event;
                isActive = false;
            }
        }

        return new LatestLocationState(latestLocationEvent, isActive);
    }

    private Instant calculateExpirationTimestamp(
            AdtEvent latestLocationEvent,
            boolean isActive,
            Duration dischargedTtl
    ) {
        // Se ativo, não expira (ou expira num futuro muito distante/null)
        // Se inativo (alta), expira em: eventTime + dischargedTtl
        if (isActive || dischargedTtl == null) {
            return null;
        }

        return latestLocationEvent.getEventTimestamp().plus(dischargedTtl);
    }

    private boolean isExpired(Instant expirationTimestamp) {
        return expirationTimestamp != null && !expirationTimestamp.isAfter(clock.instant());
    }

    private record LatestLocationState(AdtEvent latestLocationEvent, boolean isActive) {
    }

}
