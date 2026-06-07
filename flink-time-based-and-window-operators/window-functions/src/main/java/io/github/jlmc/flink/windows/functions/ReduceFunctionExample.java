package io.github.jlmc.flink.windows.functions;

import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import java.time.Duration;

/**
 * Example of using ReduceFunction.
 * It combines elements sequentially. Input, intermediate, and output types must be identical.
 */
public class ReduceFunctionExample {

    public static DataStream<SensorReading> definePipeline(DataStream<SensorReading> input) {
        return input
                .keyBy(r -> r.id)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(10)))
                .reduce(new MaxTemperatureReduce());
    }

    public static class MaxTemperatureReduce implements ReduceFunction<SensorReading> {
        @Override
        public SensorReading reduce(SensorReading r1, SensorReading r2) {
            return new SensorReading(
                    r1.id,
                    r1.timestamp.isAfter(r2.timestamp) ? r1.timestamp : r2.timestamp,
                    Math.max(r1.temperature, r2.temperature)
            );
        }
    }
}
