package io.github.jlmc.flink.windows.functions;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import java.time.Duration;

/**
 * Example of using ProcessWindowFunction.
 * Buffers all elements and provides context like window end time.
 */
public class ProcessWindowFunctionExample {

    public static DataStream<String> definePipeline(DataStream<SensorReading> input) {
        return input
                .keyBy(r -> r.id)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(10)))
                .process(new SensorCountProcessFunction());
    }

    public static class SensorCountProcessFunction extends ProcessWindowFunction<SensorReading, String, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<SensorReading> elements, Collector<String> out) {
            long count = 0;
            for (SensorReading ignored : elements) {
                count++;
            }
            out.collect("Window: " + context.window().getEnd() + " Key: " + key + " Count: " + count);
        }
    }
}
