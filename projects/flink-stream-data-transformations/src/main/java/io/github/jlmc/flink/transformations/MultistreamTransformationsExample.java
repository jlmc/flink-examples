package io.github.jlmc.flink.transformations;

import org.apache.flink.streaming.api.datastream.ConnectedStreams;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoFlatMapFunction;
import org.apache.flink.util.Collector;

/**
 * Examples of Multistream Transformations: Union and Connect.
 */
public class MultistreamTransformationsExample {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Union: Merge streams of the same type
        DataStream<String> stream1 = env.fromElements("A", "B");
        DataStream<String> stream2 = env.fromElements("C", "D");
        DataStream<String> merged = stream1.union(stream2);
        merged.print("Union");

        // 2. Connect: Connect two different types of streams
        DataStream<String> data = env.fromElements("user_1", "user_2", "user_3");
        DataStream<Boolean> control = env.fromElements(true, false, true);

        ConnectedStreams<String, Boolean> connected = data.connect(control.broadcast());

        DataStream<String> result = connected.flatMap(new CoFlatMapFunction<String, Boolean, String>() {
            private boolean shouldProcess = true;

            @Override
            public void flatMap1(String value, Collector<String> out) {
                if (shouldProcess) {
                    out.collect("Processing: " + value);
                } else {
                    out.collect("Skipping: " + value);
                }
            }

            @Override
            public void flatMap2(Boolean value, Collector<String> out) {
                shouldProcess = value;
            }
        });
        result.print("Connect");

        env.execute("Multistream Transformations Example");
    }
}
