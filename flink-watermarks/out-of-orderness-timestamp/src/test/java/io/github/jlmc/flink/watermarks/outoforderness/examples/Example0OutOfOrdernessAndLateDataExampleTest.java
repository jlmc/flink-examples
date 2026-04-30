package io.github.jlmc.flink.watermarks.outoforderness.examples;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Example0OutOfOrdernessAndLateDataExampleTest {

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
    void shouldBuildWindowsFromMonotonicEventTime() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<Example0OutOfOrdernessAndLateDataExample.SensorReading> stream = env
                .fromData(
                        "lisbon,10.1,1000",
                        "lisbon,10.2,2000",
                        "lisbon,10.3,6000",
                        "porto,20.1,7000"
                )
                .map(Example0OutOfOrdernessAndLateDataExample::parseReading);

        Example0OutOfOrdernessAndLateDataExample.definePipeline(stream)
                .addSink(new CollectSink<>());

        env.execute();

        List<String> values = CollectSink.values();
        assertThat(values).hasSize(3);
        assertThat(values).anyMatch(v -> v.contains("city: lisbon") && v.contains("count: 2"));
        assertThat(values).anyMatch(v -> v.contains("city: lisbon") && v.contains("count: 1"));
        assertThat(values).anyMatch(v -> v.contains("city: porto") && v.contains("count: 1"));
    }
}
