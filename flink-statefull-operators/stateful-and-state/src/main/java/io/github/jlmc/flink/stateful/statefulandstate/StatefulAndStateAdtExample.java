package io.github.jlmc.flink.stateful.statefulandstate;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/**
 * 1) Stateful & State.
 *
 * <p>Objetivo: mostrar, com ADT events, o que é uma aplicação stateful no Flink.
 * Cada evento é processado por chave (facilityId) e o operador mantém estado (`ValueState`) com
 * o total de eventos vistos para essa chave.</p>
 */
public class StatefulAndStateAdtExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<AdtEvent> events = env.fromData(
                new AdtEvent("m-1", "hospital-huc", "ADT_A01", 1000L),
                new AdtEvent("m-2", "hospital-huc", "ADT_A03", 2000L),
                new AdtEvent("m-3", "hospital-lisbon", "ADT_A02", 3000L)
        );

        events
                .keyBy(event -> event.facilityId)
                .process(new FacilityStateCounter())
                .print();

        env.execute("Stateful & State with ADT Events");
    }

    static class FacilityStateCounter extends KeyedProcessFunction<String, AdtEvent, String> {

        private transient ValueState<Long> totalByFacility;

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            totalByFacility = getRuntimeContext().getState(new ValueStateDescriptor<>("total-by-facility", Long.class));
        }

        @Override
        public void processElement(AdtEvent value,
                                   KeyedProcessFunction<String, AdtEvent, String>.Context ctx,
                                   Collector<String> out) throws Exception {

            long previous = totalByFacility.value() == null ? 0L : totalByFacility.value();
            long updated = previous + 1;
            totalByFacility.update(updated);

            out.collect("facility=" + value.facilityId + ", eventType=" + value.eventType + ", total=" + updated);
        }
    }

    public static class AdtEvent {
        public String messageId;
        public String facilityId;
        public String eventType;
        public long eventTimestamp;

        public AdtEvent() {
        }

        public AdtEvent(String messageId, String facilityId, String eventType, long eventTimestamp) {
            this.messageId = messageId;
            this.facilityId = facilityId;
            this.eventType = eventType;
            this.eventTimestamp = eventTimestamp;
        }
    }
}
