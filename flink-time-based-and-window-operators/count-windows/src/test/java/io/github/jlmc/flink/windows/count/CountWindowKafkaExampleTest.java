package io.github.jlmc.flink.windows.count;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CountWindowKafkaExampleTest {

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
    void shouldComputeAveragesEveryThreeEventsPerSensor() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        CountWindowByGlobalWindowKafkaExample.definePipeline(env.fromData(
                        new SensorReading("s1", Instant.parse("2024-04-05T00:00:01Z"), 10.0),
                        new SensorReading("s2", Instant.parse("2024-04-05T00:00:02Z"), 20.0),
                        new SensorReading("s1", Instant.parse("2024-04-05T00:00:03Z"), 16.0),
                        new SensorReading("s2", Instant.parse("2024-04-05T00:00:04Z"), 22.0),
                        new SensorReading("s1", Instant.parse("2024-04-05T00:00:05Z"), 19.0),
                        new SensorReading("s2", Instant.parse("2024-04-05T00:00:06Z"), 24.0)
                ))
                .addSink(new CollectSink<>());

        env.execute("count-window-global-window-test");

        List<SensorReading> values = CollectSink.values();
        assertThat(values).hasSize(2);

        Map<String, Double> avgBySensor = values.stream()
                .collect(Collectors.toMap(
                        value -> value.id,
                        value -> value.temperature
                ));

        assertThat(avgBySensor)
                .containsEntry("s1", 16.0)
                .containsEntry("s2", 22.5);
    }
}
