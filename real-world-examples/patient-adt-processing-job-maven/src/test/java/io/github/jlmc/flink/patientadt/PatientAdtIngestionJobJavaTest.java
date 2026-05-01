package io.github.jlmc.flink.patientadt;

import io.github.jlmc.flink.patientadt.app.model.AdtEvent;
import io.github.jlmc.flink.patientadt.app.model.AdtPatientLastLocation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.graph.StreamGraph;
import org.apache.flink.streaming.api.graph.StreamNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientAdtIngestionJobJavaTest {

    @BeforeEach
    void setUp() {
        CollectListSink.clear();
    }

    @Test
    void shouldDefinePipelineWithCollectionSourceAndCollectSink() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(2);
        List<Tuple2<String, AdtEvent>> inputEvents = List.of(
                Tuple2.of("ACC_1_PAT_1", event("ACC_1", "PAT_1", "A01", "WARD-A", "2026-01-01T08:00:00Z")),
                Tuple2.of("ACC_1_PAT_1", event("ACC_1", "PAT_1", "A02", "WARD-B", "2026-01-01T09:00:00Z"))
        );

        PatientAdtIngestionJobJava.definePipeline(
                        env.fromCollection(inputEvents),
                        1,
                        5,
                        2
                )
                .addSink(new CollectListSink());

        env.execute("shouldDefinePipelineWithCollectionSourceAndCollectSink");

        List<AdtPatientLastLocation> output = new ArrayList<>(CollectListSink.values());
        assertTrue(output.size() >= 2);

        assertTrue(output.stream().anyMatch(it ->
                "ACC_1".equals(it.accountId())
                        && "PAT_1".equals(it.patientId())
                        && "WARD-B".equals(it.locationId())
                        && it.isActive()
        ));
    }

    @Test
    void shouldApplyConfiguredParallelismToMainOperators() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(2);
        int parallelism = 2;

        PatientAdtIngestionJobJava.definePipeline(
                env.fromCollection(List.of(Tuple2.of("ACC_1_PAT_1", event("ACC_1", "PAT_1", "A01", "WARD-A", "2026-01-01T08:00:00Z")))),
                parallelism,
                5,
                2
        ).addSink(new CollectListSink()).name("Collect Sink");

        StreamGraph streamGraph = env.getStreamGraph();
        Map<String, Integer> operatorsParallelism = streamGraph.getStreamNodes()
                .stream()
                .collect(Collectors.toMap(
                        StreamNode::getOperatorName,
                        StreamNode::getParallelism,
                        (left, right) -> right
                ));

        assertEquals(parallelism, operatorsParallelism.get("Extract AdtEvent"));
        assertEquals(parallelism, operatorsParallelism.get("Resolve Patient Location"));
        assertTrue(operatorsParallelism.size() >= 3);
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

    private static class CollectListSink implements SinkFunction<AdtPatientLastLocation> {
        private static final List<AdtPatientLastLocation> VALUES = new CopyOnWriteArrayList<>();

        @Override
        public void invoke(AdtPatientLastLocation value, Context context) {
            VALUES.add(value);
        }

        static List<AdtPatientLastLocation> values() {
            return VALUES;
        }

        static void clear() {
            VALUES.clear();
        }
    }
}
