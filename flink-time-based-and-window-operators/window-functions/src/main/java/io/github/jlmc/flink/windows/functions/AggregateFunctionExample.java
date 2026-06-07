package io.github.jlmc.flink.windows.functions;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import java.time.Duration;

/**
 * Example of using AggregateFunction.
 * It allows different types for input, accumulator, and output.
 */
public class AggregateFunctionExample {

    public static DataStream<Double> definePipeline(DataStream<SensorReading> input) {
        return input
                .keyBy(r -> r.id)
                .window(TumblingEventTimeWindows.of(Duration.ofMinutes(10)))
                .aggregate(new AverageAggregate());
    }

    public static class AverageAggregate implements AggregateFunction<SensorReading, AverageAccumulator, Double> {
        @Override
        public AverageAccumulator createAccumulator() {
            return new AverageAccumulator();
        }

        @Override
        public AverageAccumulator add(SensorReading value, AverageAccumulator accumulator) {
            accumulator.sum += value.temperature;
            accumulator.count++;
            return accumulator;
        }

        @Override
        public Double getResult(AverageAccumulator accumulator) {
            return accumulator.sum / accumulator.count;
        }

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
}
