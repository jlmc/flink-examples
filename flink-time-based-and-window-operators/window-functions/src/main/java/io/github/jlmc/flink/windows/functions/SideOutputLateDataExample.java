package io.github.jlmc.flink.windows.functions;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import java.time.Duration;

/**
 * Example of handling late data using Side Outputs.
 */
public class SideOutputLateDataExample {

    public static final OutputTag<SensorReading> LATE_DATA_TAG = new OutputTag<SensorReading>("late-readings"){};

    public static SingleOutputStreamOperator<String> definePipeline(DataStream<SensorReading> input) {
        return input
                .keyBy(r -> r.id)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(10)))
                .sideOutputLateData(LATE_DATA_TAG)
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
