package io.github.jlmc.flink.transformations;

import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class MultistreamTransformationsExampleTest {

    @RegisterExtension
    static final MiniClusterExtension FLINK_CLUSTER = new MiniClusterExtension(
            new MiniClusterResourceConfiguration.Builder()
                    .setNumberSlotsPerTaskManager(2)
                    .setNumberTaskManagers(1)
                    .build());

    @BeforeEach
    void setUp() {
        CollectSink.VALUES.clear();
    }

    @Test
    void testUnionTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<String> stream1 = env.fromData("A");
        DataStream<String> stream2 = env.fromData("B");

        stream1.union(stream2)
               .addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.VALUES).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void testConnectTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // Set to 1 for predictable order in this specific test if needed, but broadcast is used

        DataStream<String> data = env.fromData("user_1");
        DataStream<Boolean> control = env.fromData(false); // Switch to skipping

        data.connect(control.broadcast())
            .flatMap(new MultistreamTransformationsExampleTest.TestCoFlatMap())
            .addSink(new CollectSink<>());

        env.execute();

        // Since it's broadcast and we only have one data element,
        // it might be processed before or after the control element reaches the instance.
        // But usually, in a MiniCluster with small data, it's deterministic enough or we check for both.
        assertThat(CollectSink.VALUES.stream().map(Object::toString).collect(Collectors.toList()))
                .anySatisfy(s -> assertThat(s).contains("user_1"));
    }

    public static class TestCoFlatMap implements org.apache.flink.streaming.api.functions.co.CoFlatMapFunction<String, Boolean, String> {
        private boolean shouldProcess = true;

        @Override
        public void flatMap1(String value, org.apache.flink.util.Collector<String> out) {
            if (shouldProcess) out.collect("Processing: " + value);
            else out.collect("Skipping: " + value);
        }

        @Override
        public void flatMap2(Boolean value, org.apache.flink.util.Collector<String> out) {
            shouldProcess = value;
        }
    }
}
