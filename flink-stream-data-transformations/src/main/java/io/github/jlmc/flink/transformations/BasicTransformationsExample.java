package io.github.jlmc.flink.transformations;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Examples of Basic Transformations (Stateless): Map, Filter, FlatMap.
 */
public class BasicTransformationsExample {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<String> input = env.fromData("flink", "stream", "data", "transformation");

        // 1. Map: One element in, one element out
        DataStream<String> upperCase = input.map(s -> s.toUpperCase(), Types.STRING);
        upperCase.print("Map (Upper Case)");

        // 2. Filter: Only elements that match the condition
        DataStream<String> filtered = input.filter(s -> s.startsWith("f"));
        filtered.print("Filter (Starts with 'f')");

        // 3. FlatMap: One element in, zero, one or many elements out
        DataStream<String> words = input.flatMap((String s, org.apache.flink.util.Collector<String> out) -> {
            for (char c : s.toCharArray()) {
                out.collect(String.valueOf(c));
            }
        }).returns(org.apache.flink.api.common.typeinfo.Types.STRING);
        words.print("FlatMap (Characters)");

        env.execute("Basic Transformations Example");
    }
}
