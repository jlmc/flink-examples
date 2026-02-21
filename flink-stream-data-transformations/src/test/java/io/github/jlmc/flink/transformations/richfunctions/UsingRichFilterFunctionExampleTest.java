package io.github.jlmc.flink.transformations.richfunctions;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsingRichFilterFunctionExampleTest {

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
    void shouldFilterMarvelHeroesOnly() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<String> input = env.fromData(
                "IronMan", "Superman", "CaptainAmerica", "Batman", "Thor", "WonderWoman"
        );

        UsingRichFilterFunctionExample.createPipeline(input)
                                      .addSink(new CollectSink<>());

        env.execute();

        List<String> results = CollectSink.values();
        assertThat(results).containsExactlyInAnyOrder("IronMan", "CaptainAmerica", "Thor");
        assertThat(results).doesNotContain("Superman", "Batman", "WonderWoman");
    }
}
