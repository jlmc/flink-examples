package io.github.jlmc.flink.transformations;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DistributionTransformationsExampleTest {

    @RegisterExtension
    static final MiniClusterExtension FLINK_CLUSTER = new MiniClusterExtension(
            new MiniClusterResourceConfiguration.Builder()
                    .setNumberSlotsPerTaskManager(4)
                    .setNumberTaskManagers(1)
                    .build());

    @BeforeEach
    void setUp() {
        CollectSink.clear();
    }

    @Test
    void testRebalanceTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(4);

        DataStream<Long> numbers = env.fromSequence(1, 100);

        numbers.rebalance()
               .map(n -> n)
               .addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.values()).hasSize(100);
        
        // Since it's rebalanced, all values should be present
        Set<Object> results = new HashSet<>(CollectSink.values());
        assertThat(results).hasSize(100);
    }
}
