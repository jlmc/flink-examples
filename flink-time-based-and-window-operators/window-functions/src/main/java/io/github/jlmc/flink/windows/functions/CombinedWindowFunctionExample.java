package io.github.jlmc.flink.windows.functions;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import java.time.Duration;

/**
 * Example of combining AggregateFunction (Incremental) and ProcessWindowFunction (Full Window Context).
 * Efficiently aggregates while still providing window metadata.
 */
public class CombinedWindowFunctionExample {

    public static DataStream<String> definePipeline(DataStream<SensorReading> input) {
        return input
                .keyBy(r -> r.id)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(10)))
                .aggregate(new AverageAggregate(), new AverageProcessWindowFunction());
    }

    public static class AverageAggregate implements AggregateFunction<SensorReading, AverageAccumulator, Double> {
        @Override
        public AverageAccumulator createAccumulator() { return new AverageAccumulator(); }
        @Override
        public AverageAccumulator add(SensorReading value, AverageAccumulator acc) {
            acc.sum += value.temperature;
            acc.count++;
            return acc;
        }
        @Override
        public Double getResult(AverageAccumulator acc) { return acc.sum / acc.count; }
        @Override
        public AverageAccumulator merge(AverageAccumulator a, AverageAccumulator b) {
            a.sum += b.sum;
            a.count += b.count;
            return a;
        }
    }

    public static class AverageAccumulator {
        public double sum = 0;
        public int count = 0;
    }

    public static class AverageProcessWindowFunction extends ProcessWindowFunction<Double, String, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<Double> averages, Collector<String> out) {
            Double avg = averages.iterator().next();
            out.collect("Window: " + context.window().getEnd() + " Key: " + key + " Avg: " + avg);
        }
    }
}
