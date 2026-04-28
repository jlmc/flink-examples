package io.github.jlmc.flink.windows.tumbling;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TumblingTimeWindowAssignerExampleThreeTest {

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
    void shouldComputeMaxTemperaturePerCityAndWindow() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        List<String> input = List.of(
                "porto,18.2,1000",
                "porto,19.0,2000",
                "porto,20.0,61000",
                "lisbon,22.0,62000",
                "lisbon,21.5,65000"
        );

        TumblingTimeWindowAssignerExampleThree.definePipeline(env.fromData(input))
                .addSink(new CollectSink<>());

        env.execute("TumblingTimeWindowAssignerExampleThreeTest");

        List<TumblingTimeWindowAssignerExampleThree.CityTemperature> values = CollectSink.values();
        assertThat(values).hasSize(3);

        assertThat(values)
                .anySatisfy(value -> {
                    assertThat(value.getCity()).isEqualTo("porto");
                    assertThat(value.getTemperature()).isEqualTo(19.0f);
                })
                .anySatisfy(value -> {
                    assertThat(value.getCity()).isEqualTo("porto");
                    assertThat(value.getTemperature()).isEqualTo(20.0f);
                })
                .anySatisfy(value -> {
                    assertThat(value.getCity()).isEqualTo("lisbon");
                    assertThat(value.getTemperature()).isEqualTo(22.0f);
                });
    }
}
