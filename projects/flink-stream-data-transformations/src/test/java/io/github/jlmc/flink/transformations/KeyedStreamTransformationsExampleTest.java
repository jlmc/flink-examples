package io.github.jlmc.flink.transformations;

import io.github.jlmc.flink.transformations.KeyedStreamTransformationsExample.WordEntry;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class KeyedStreamTransformationsExampleTest {

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
    void testReduceTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<WordEntry> stream = env.fromData(
                new WordEntry("apple", 1),
                new WordEntry("apple", 1),
                new WordEntry("banana", 1)
        );

        stream.keyBy(entry -> entry.word)
              .reduce((val1, val2) -> new WordEntry(val1.word, val1.count + val2.count))
              .addSink(new CollectSink<>());

        env.execute();

        // The sink will collect all intermediate results because it's a stream
        // apple:1, apple:2, banana:1
        List<String> results = CollectSink.VALUES.stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        assertThat(results).contains(
                "WordEntry{word='apple', count=1}",
                "WordEntry{word='apple', count=2}",
                "WordEntry{word='banana', count=1}"
        );
    }

    @Test
    void testSumTransformation() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<Tuple2<String, Integer>> tupleStream = env.fromData(
                Tuple2.of("cat1", 10),
                Tuple2.of("cat1", 5)
        );

        tupleStream.keyBy(t -> t.f0)
                   .sum(1)
                   .addSink(new CollectSink<>());

        env.execute();

        List<String> results = CollectSink.VALUES.stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        assertThat(results).contains("(cat1,10)", "(cat1,15)");
    }
}
