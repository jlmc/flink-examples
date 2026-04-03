package io.github.jlmc.flink.windows.functions;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import java.time.Duration;

/**
 * Real-world example using Window Functions (AggregateFunction).
 * Demonstrates how to use a more complex aggregation function to calculate the average.
 */
public class WindowFunctionsKafkaExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<SensorReading> source = KafkaSource.<SensorReading>builder()
                .setBootstrapServers("kafka:19092")
                .setTopics("sensors-data")
                .setGroupId("window-functions-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(SensorReading.class))
                .build();

        WatermarkStrategy<SensorReading> watermarkStrategy = WatermarkStrategy
                .<SensorReading>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                .withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli());

        DataStream<SensorReading> sensorStream = env.fromSource(source, watermarkStrategy, "Kafka Sensor Source");

        // Uses AggregateFunction to calculate the average incrementally
        sensorStream
                .keyBy(r -> r.id)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(10)))
                .aggregate(new AverageAggregate())
                .print();

        env.execute("Flink Window Functions Kafka Example");
    }

    // Accumulator: (sum, count)
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
