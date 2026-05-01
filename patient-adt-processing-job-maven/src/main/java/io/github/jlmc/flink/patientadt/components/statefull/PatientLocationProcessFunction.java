package io.github.jlmc.flink.patientadt.components.statefull;

import io.github.jlmc.flink.patientadt.model.AdtEvent;
import io.github.jlmc.flink.patientadt.model.AdtPatientLastLocation;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class PatientLocationProcessFunction extends KeyedProcessFunction<String, AdtEvent, AdtPatientLastLocation> {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PatientLocationProcessFunction.class);


    private final Duration eventTtl;
    private final Duration dischargedTtl;

    // Estado que guarda o histórico: Timestamp -> Evento
    // Usamos MapState porque é mais eficiente para checkpoints do que ListState se precisarmos de remover itens específicos.
    private transient MapState<Long, AdtEvent> patientAdtEventsHistoryState;
    private transient AdtPatientLastLocationResolver adtPatientLastLocationResolver;


    public PatientLocationProcessFunction() {
        this(Duration.ofDays(7L), Duration.ofDays(30L));
    }

    public PatientLocationProcessFunction(Duration eventTtl, Duration dischargedTtl) {
        this.eventTtl = eventTtl;
        this.dischargedTtl = dischargedTtl;
    }

    @Override
    public void open(OpenContext openContext) throws Exception {
        super.open(openContext);

        // Configuração de TTL para evitar vazamento de memória (ex: limpar após 7 dias de inatividade)
        StateTtlConfig ttlConfig = StateTtlConfig
                //.newBuilder(Time.days(7))
                .newBuilder(eventTtl)
                .setUpdateType(StateTtlConfig.UpdateType.OnReadAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .cleanupIncrementally(100, true)
                .build();

        MapStateDescriptor<Long, AdtEvent> descriptor = new MapStateDescriptor<>(
                "patientAdtEventsHistoryState",
                Long.class,
                AdtEvent.class
        );

        descriptor.enableTimeToLive(ttlConfig);

        patientAdtEventsHistoryState = getRuntimeContext().getMapState(descriptor);

        adtPatientLastLocationResolver = new AdtPatientLastLocationResolver();
    }

    @Override
    public void processElement(
            AdtEvent event,
            KeyedProcessFunction<String, AdtEvent, AdtPatientLastLocation>.Context ctx, Collector<AdtPatientLastLocation> out
    ) throws Exception {
        LOGGER.info("Processing event [accountId: {} patientId: {}], at {} for patient {}", event.accountId, event.patientId,  event.eventTimestamp, event.patientId);

        // 1. Persist the event in the MapState using its timestamp as the key
        long epochMilli = event.eventTimestamp.toEpochMilli();
        patientAdtEventsHistoryState.put(epochMilli, event);

        AdtPatientLastLocation adtPatientLastLocation = resolveLatestValid();

        if (adtPatientLastLocation != null) {
            LOGGER.info("Emitting latest valid location for [accountId: {} patientId: {}]: {}", event.accountId, event.patientId, adtPatientLastLocation);
            out.collect(adtPatientLastLocation);
        }
    }

    private AdtPatientLastLocation resolveLatestValid() throws Exception {
        try {
            Iterable<Map.Entry<Long, AdtEvent>> entries = patientAdtEventsHistoryState.entries();

            List<AdtEvent> allEventsSorted = StreamSupport.stream(entries.spliterator(), false)
                    .filter(Objects::nonNull)
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .map(Map.Entry::getValue)
                    .toList();


            return adtPatientLastLocationResolver.resolveLatestValid(allEventsSorted, dischargedTtl);
        } catch (Exception e) {
            LOGGER.error("Error accessing MapState for resolution: {}", e.getMessage());
            throw e;
        }
    }
}
