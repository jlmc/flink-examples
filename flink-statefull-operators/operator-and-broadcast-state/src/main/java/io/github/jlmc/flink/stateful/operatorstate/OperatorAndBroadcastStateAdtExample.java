package io.github.jlmc.flink.stateful.operatorstate;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.util.Collector;

/**
 * 3) Operator State + Broadcast State + Stateful Source Function.
 */
public class OperatorAndBroadcastStateAdtExample {

    private static final MapStateDescriptor<String, String> RULES_DESCRIPTOR =
            new MapStateDescriptor<>("adt-rules", Types.STRING, Types.STRING);

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<AdtEvent> adtEvents = env.addSource(new StatefulAdtSource())
                .map((MapFunction<String, AdtEvent>) value -> {
                    String[] parts = value.split(",");
                    return new AdtEvent(parts[0], parts[1], parts[2], Long.parseLong(parts[3]));
                });

        DataStream<String> rules = env.fromData(
                "ADT_A01=ADMISSION",
                "ADT_A02=TRANSFER",
                "ADT_A03=DISCHARGE"
        );

        adtEvents
                .keyBy(event -> event.facilityId)
                .connect(rules.broadcast(RULES_DESCRIPTOR))
                .process(new EventTypeEnricher())
                .print();

        env.execute("Operator and Broadcast State with ADT Events");
    }

    static class EventTypeEnricher extends KeyedBroadcastProcessFunction<String, AdtEvent, String, String> {

        @Override
        public void processElement(AdtEvent value,
                                   KeyedBroadcastProcessFunction<String, AdtEvent, String, String>.ReadOnlyContext ctx,
                                   Collector<String> out) throws Exception {
            ReadOnlyBroadcastState<String, String> rules = ctx.getBroadcastState(RULES_DESCRIPTOR);
            String meaning = rules.get(value.eventType);
            out.collect("facility=" + value.facilityId + ", eventType=" + value.eventType + ", meaning=" + meaning);
        }

        @Override
        public void processBroadcastElement(String value,
                                            KeyedBroadcastProcessFunction<String, AdtEvent, String, String>.Context ctx,
                                            Collector<String> out) throws Exception {
            String[] parts = value.split("=");
            ctx.getBroadcastState(RULES_DESCRIPTOR).put(parts[0], parts[1]);
        }
    }

    static class StatefulAdtSource extends RichParallelSourceFunction<String> implements CheckpointedFunction {

        private volatile boolean running = true;
        private long nextIdx;
        private transient ListState<Long> operatorState;

        @Override
        public void run(SourceContext<String> ctx) {
            while (running && nextIdx < 3) {
                long ts = 1000L + nextIdx * 1000L;
                String eventType = nextIdx == 0 ? "ADT_A01" : (nextIdx == 1 ? "ADT_A02" : "ADT_A03");
                ctx.collect("m-" + nextIdx + ",hospital-huc," + eventType + "," + ts);
                nextIdx++;
            }
        }

        @Override
        public void cancel() {
            running = false;
        }

        @Override
        public void snapshotState(FunctionSnapshotContext context) throws Exception {
            operatorState.clear();
            operatorState.add(nextIdx);
        }

        @Override
        public void initializeState(FunctionInitializationContext context) throws Exception {
            ListStateDescriptor<Long> descriptor = new ListStateDescriptor<>("source-offset", Long.class);
            operatorState = context.getOperatorStateStore().getListState(descriptor);
            if (context.isRestored()) {
                for (Long value : operatorState.get()) {
                    nextIdx = value;
                }
            }
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
