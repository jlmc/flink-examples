package io.github.jlmc.flink.windows.functions;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SideOutputLateDataExampleTest {

    static final MiniClusterWithClientResource FLINK_CLUSTER =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());

    @BeforeAll
    static void beforeAll() throws Exception {
        FLINK_CLUSTER.before();
    }

    @AfterAll
    static void afterAll() {
        FLINK_CLUSTER.after();
    }

    @BeforeEach
    void setUp() {
        CollectSink.clear();
        LateCollectSink.clear();
    }

    @Test
    void shouldCaptureLateDataInSideOutput() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        Instant now = Instant.parse("2024-01-01T10:00:00Z");

        // We use a list of elements with timestamps.
        // Element 1: On-time (10:00)
        // Element 2: Way ahead (10:20) - This will advance the watermark to 10:19:59.999
        // Element 3: Late (10:05) - Since watermark is at 10:19, this is late for the 10:00-10:10 window.
        List<SensorReading> input = List.of(
                new SensorReading("s1", now, 10.0),
                new SensorReading("s1", now.plus(Duration.ofMinutes(20)), 20.0),
                new SensorReading("s1", now.plus(Duration.ofMinutes(5)), 50.0)
        );

        // Use a custom WatermarkStrategy to ensure the watermark advances before the late element arrives
        DataStream<SensorReading> stream = env.fromData(input)
                .assignTimestampsAndWatermarks(
                        new WatermarkStrategy<SensorReading>() {
                            @Override
                            public org.apache.flink.api.common.eventtime.WatermarkGenerator<SensorReading> createWatermarkGenerator(org.apache.flink.api.common.eventtime.WatermarkGeneratorSupplier.Context context) {
                                return new org.apache.flink.api.common.eventtime.WatermarkGenerator<SensorReading>() {
                                    private long maxTimestamp = Long.MIN_VALUE;
                                    @Override
                                    public void onEvent(SensorReading event, long eventTimestamp, org.apache.flink.api.common.eventtime.WatermarkOutput output) {
                                        maxTimestamp = Math.max(maxTimestamp, eventTimestamp);
                                        // Immediately advance watermark for elements with high timestamps
                                        output.emitWatermark(new org.apache.flink.api.common.eventtime.Watermark(maxTimestamp - 1));
                                    }
                                    @Override
                                    public void onPeriodicEmit(org.apache.flink.api.common.eventtime.WatermarkOutput output) {
                                        output.emitWatermark(new org.apache.flink.api.common.eventtime.Watermark(maxTimestamp - 1));
                                    }
                                };
                            }
                        }.withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli())
                );

        SingleOutputStreamOperator<String> result = SideOutputLateDataExample.definePipeline(stream);

        result.addSink(new CollectSink<>());
        result.getSideOutput(SideOutputLateDataExample.LATE_DATA_TAG).addSink(new LateCollectSink());

        env.execute("SideOutputLateDataExampleTest");

        List<String> mainResults = CollectSink.values();
        List<SensorReading> lateResults = LateCollectSink.values();

        assertThat(mainResults).hasSize(2);
        assertThat(lateResults).hasSize(1);
        assertThat(lateResults.get(0).temperature).isEqualTo(50.0);
    }

    public static class LateCollectSink implements SinkFunction<SensorReading> {
        public static final List<SensorReading> VALUES = Collections.synchronizedList(new ArrayList<>());
        @Override
        public void invoke(SensorReading value, Context context) { VALUES.add(value); }
        public static List<SensorReading> values() { return VALUES; }
        public static void clear() { VALUES.clear(); }
    }
}
