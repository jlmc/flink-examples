package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.util.Collector;

import java.util.Arrays;

public class Tokenizer implements FlatMapFunction<String, Tuple2<String, Long>> {
    @Override
    public void flatMap(String line, Collector<Tuple2<String, Long>> collector) throws Exception {
        // Normalize the line: trim whitespace, remove non-alphanumeric chars (except space), convert to lowercase
        String normalizedLine = line.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "");

        // Split the line by any whitespace character
        Arrays.stream(normalizedLine.split("\\s+"))
                .filter(word -> !word.isEmpty())
                .forEach(word -> collector.collect(new Tuple2<>(word, 1L)));
    }
}
