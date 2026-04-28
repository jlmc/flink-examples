package io.github.jlmc.flink.windows.global.examples;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalTimeWindowAssignerTest {

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
    void shouldKeepMaxTemperaturePerCityInGlobalWindow() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        GlobalTimeWindowAssigner.definePipeline(env.fromData(
                        "porto,18.0,1712275201000",
                        "lisbon,20.0,1712275202000",
                        "porto,25.0,1712275203000",
                        "lisbon,19.0,1712275204000",
                        "porto,23.0,1712275205000",
                        "lisbon,21.0,1712275206000",
                        "porto,17.0,1712275207000",
                        "lisbon,22.0,1712275208000",
                        "porto,24.0,1712275209000",
                        "lisbon,18.0,1712275210000"
                ))
                .addSink(new CollectSink<>());

        env.execute("global-window-test");

        List<GlobalTimeWindowAssigner.CityTemperature> values = CollectSink.values();
        assertThat(values).hasSize(2);

        Map<String, Float> maxByCity = values.stream()
                .collect(Collectors.toMap(
                        GlobalTimeWindowAssigner.CityTemperature::getCity,
                        GlobalTimeWindowAssigner.CityTemperature::getTemperature,
                        Float::max
                ));

        assertThat(maxByCity)
                .containsEntry("porto", 25.0f)
                .containsEntry("lisbon", 22.0f);
    }
}
