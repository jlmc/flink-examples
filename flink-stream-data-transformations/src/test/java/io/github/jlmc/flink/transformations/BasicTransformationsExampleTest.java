package io.github.jlmc.flink.transformations;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicTransformationsExampleTest {

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
    void testMapTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<String> input = env.fromData("flink", "stream");

        //noinspection Convert2MethodRef
        input.map(s -> s.toUpperCase(), Types.STRING)
             .addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.values()).containsExactlyInAnyOrder("FLINK", "STREAM");
    }

    @Test
    void testFilterTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<String> input = env.fromData("flink", "stream", "data");

        input.filter(s -> s.startsWith("f"))
             .addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.values()).containsExactly("flink");
    }

    @Test
    void testFlatMapTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<String> input = env.fromData("abc");

        input.flatMap((String s, org.apache.flink.util.Collector<String> out) -> {
            for (char c : s.toCharArray()) {
                out.collect(String.valueOf(c));
            }
        }, Types.STRING)
             .addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.values()).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void testFlatMapWithFilterTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<String> input = env.fromData("white", "black", "white", "gray", "black", "white");

        input.flatMap((String color, org.apache.flink.util.Collector<String> collector) -> {
                    switch (color) {
                        case "white":
                            collector.collect(color.toUpperCase());
                            break;
                        case "black":
                            collector.collect(color);
                            collector.collect(color);
                            break;
                        default:
                            break;
                    }
        }, Types.STRING)
       .addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.values()).containsExactlyInAnyOrder("WHITE", "black", "black", "WHITE", "black", "black", "WHITE");
    }
}
