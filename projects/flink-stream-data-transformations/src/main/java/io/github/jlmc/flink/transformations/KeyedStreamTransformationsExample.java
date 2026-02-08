package io.github.jlmc.flink.transformations;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Examples of KeyedStream Transformations (Stateful): keyBy, Reduce, Sum.
 */
public class KeyedStreamTransformationsExample {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<WordEntry> stream = env.fromElements(
                new WordEntry("apple", 1),
                new WordEntry("apple", 1),
                new WordEntry("banana", 1),
                new WordEntry("orange", 1),
                new WordEntry("banana", 1)
        );

        // 1. keyBy and Reduce
        DataStream<WordEntry> counts = stream
                .keyBy(entry -> entry.word)
                .reduce((val1, val2) -> new WordEntry(val1.word, val1.count + val2.count));
        counts.print("Reduce (Word Count)");

        // 2. keyBy and Sum
        // Using Tuple for sum example as it's easier with sum(index)
        DataStream<Tuple2<String, Integer>> tupleStream = env.fromElements(
                Tuple2.of("category1", 10),
                Tuple2.of("category2", 20),
                Tuple2.of("category1", 5),
                Tuple2.of("category2", 15)
        );

        DataStream<Tuple2<String, Integer>> totals = tupleStream
                .keyBy(t -> t.f0)
                .sum(1);
        totals.print("Sum (Category Totals)");

        env.execute("KeyedStream Transformations Example");
    }

    public static class WordEntry {
        public String word;
        public int count;

        public WordEntry() {}

        public WordEntry(String word, int count) {
            this.word = word;
            this.count = count;
        }

        @Override
        public String toString() {
            return "WordEntry{word='" + word + "', count=" + count + "}";
        }
    }
}
