package io.github.jlmc.flink.windows.functions;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReduceFunctionExampleTest {

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
    }

    @Test
    void shouldReduceToMaxTemperaturePerWindow() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        Instant now = Instant.parse("2024-01-01T10:00:00Z");

        List<SensorReading> input = List.of(
                new SensorReading("s1", now, 20.0),
                new SensorReading("s1", now.plus(Duration.ofMinutes(1)), 25.0),
                new SensorReading("s1", now.plus(Duration.ofMinutes(11)), 30.0)
        );

        DataStream<SensorReading> stream = env.fromData(input)
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<SensorReading>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli())
                );

        ReduceFunctionExample.definePipeline(stream)
                .addSink(new CollectSink<>());

        env.execute("ReduceFunctionExampleTest");

        List<SensorReading> results = CollectSink.values();
        assertThat(results).hasSize(2);
        assertThat(results).extracting("temperature").containsExactlyInAnyOrder(25.0, 30.0);
    }
}
