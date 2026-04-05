package io.github.jlmc.flink.windows;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WindowOperatorExamplesTest {

    @RegisterExtension
    static final MiniClusterExtension FLINK_CLUSTER = new MiniClusterExtension(
            new MiniClusterResourceConfiguration.Builder()
                    .setNumberSlotsPerTaskManager(2)
                    .setNumberTaskManagers(1)
                    .build());

    @BeforeEach
    void setUp() {
        CollectSink.clear();
    }

    @Test
    void testDefinePipeline() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        Instant baseTime = Instant.parse("2026-04-05T12:00:00Z");

        DataStream<WindowOperatorExamples.Bet> input = env.fromData(
                new WindowOperatorExamples.Bet("user1", baseTime.plusSeconds(1), 100.0, new HashMap<>(Map.of("market", "football", "odds", "2.5"))),
                new WindowOperatorExamples.Bet("user2", baseTime.plusSeconds(2), 150.0, new HashMap<>(Map.of("market", "football", "odds", "2.5"))),
                new WindowOperatorExamples.Bet("user1", baseTime.plusSeconds(3), 5.6, new HashMap<>(Map.of("market", "football", "odds", "2.5"))),
                new WindowOperatorExamples.Bet("user1", baseTime.plusSeconds(11), 10.0, new HashMap<>(Map.of("market", "football", "odds", "2.5")))
        ).assignTimestampsAndWatermarks(
                org.apache.flink.api.common.eventtime.WatermarkStrategy
                        .<WindowOperatorExamples.Bet>forMonotonousTimestamps()
                        .withTimestampAssigner((bet, timestamp) -> bet.timestamp().toEpochMilli())
        );

        WindowOperatorExamples.definePipeline(input)
                .addSink(new CollectSink<WindowOperatorExamples.UserBetTotal>());

        env.execute();

        java.util.List<WindowOperatorExamples.UserBetTotal> results = CollectSink.values();

        assertThat(results)
                .hasSize(3);

        assertThat(results)
                .filteredOn(bet -> bet.userId().equals("user1"))
                .extracting(WindowOperatorExamples.UserBetTotal::value)
                .containsExactlyInAnyOrder(105.6, 10.0);

        assertThat(results)
                .filteredOn(bet -> bet.userId().equals("user2"))
                .extracting(WindowOperatorExamples.UserBetTotal::value)
                .containsExactly(150.0);
    }
}
