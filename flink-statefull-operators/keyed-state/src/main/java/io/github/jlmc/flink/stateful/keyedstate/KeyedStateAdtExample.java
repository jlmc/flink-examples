package io.github.jlmc.flink.stateful.keyedstate;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/**
 * 2) Keyed State (ValueState, ListState, MapState, ReducingState, AggregatingState, TTL).
 */
public class KeyedStateAdtExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.fromData(
                        new AdtEvent("m-1", "hospital-huc", "ADT_A01", 1000L),
                        new AdtEvent("m-2", "hospital-huc", "ADT_A02", 1500L),
                        new AdtEvent("m-3", "hospital-huc", "ADT_A03", 2200L)
                )
                .keyBy(event -> event.facilityId)
                .process(new KeyedStateInspector())
                .print();

        env.execute("Keyed State with ADT Events");
    }

    static class KeyedStateInspector extends KeyedProcessFunction<String, AdtEvent, String> {

        private transient ValueState<Long> totalEvents;
        private transient ListState<String> recentEventTypes;
        private transient MapState<String, Long> eventTypeCounters;
        private transient ReducingState<Long> reducingCounter;
        private transient AggregatingState<AdtEvent, String> eventTypeSummary;

        @Override
        public void open(Configuration parameters) {
            StateTtlConfig ttlConfig = StateTtlConfig
                    .newBuilder(Time.minutes(30))
                    .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                    .neverReturnExpired()
                    .build();

            ValueStateDescriptor<Long> valueDescriptor = new ValueStateDescriptor<>("total-events", Long.class);
            valueDescriptor.enableTimeToLive(ttlConfig);
            totalEvents = getRuntimeContext().getState(valueDescriptor);

            recentEventTypes = getRuntimeContext().getListState(new ListStateDescriptor<>("recent-event-types", String.class));
            eventTypeCounters = getRuntimeContext().getMapState(new MapStateDescriptor<>("event-type-counters", String.class, Long.class));
            reducingCounter = getRuntimeContext().getReducingState(new ReducingStateDescriptor<>("reducing-counter", (ReduceFunction<Long>) Long::sum, Long.class));

            eventTypeSummary = getRuntimeContext().getAggregatingState(new AggregatingStateDescriptor<>(
                    "aggregating-state-summary",
                    new AggregateFunction<AdtEvent, SummaryAccumulator, String>() {
                        @Override
                        public SummaryAccumulator createAccumulator() {
                            return new SummaryAccumulator();
                        }

                        @Override
                        public SummaryAccumulator add(AdtEvent value, SummaryAccumulator accumulator) {
                            accumulator.total++;
                            if ("ADT_A01".equals(value.eventType)) {
                                accumulator.admissions++;
                            }
                            return accumulator;
                        }

                        @Override
                        public String getResult(SummaryAccumulator accumulator) {
                            return "total=" + accumulator.total + ", admissions=" + accumulator.admissions;
                        }

                        @Override
                        public SummaryAccumulator merge(SummaryAccumulator a, SummaryAccumulator b) {
                            a.total += b.total;
                            a.admissions += b.admissions;
                            return a;
                        }
                    },
                    SummaryAccumulator.class
            ));
        }

        @Override
        public void processElement(AdtEvent value,
                                   KeyedProcessFunction<String, AdtEvent, String>.Context ctx,
                                   Collector<String> out) throws Exception {
            long current = totalEvents.value() == null ? 0L : totalEvents.value();
            totalEvents.update(current + 1);

            recentEventTypes.add(value.eventType);

            Long currentType = eventTypeCounters.get(value.eventType);
            eventTypeCounters.put(value.eventType, (currentType == null ? 0L : currentType) + 1L);

            reducingCounter.add(1L);
            eventTypeSummary.add(value);

            out.collect("facility=" + value.facilityId
                    + ", valueState=" + totalEvents.value()
                    + ", reducingState=" + reducingCounter.get()
                    + ", aggregatingState=" + eventTypeSummary.get());
        }
    }

    static class SummaryAccumulator {
        long total;
        long admissions;
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
