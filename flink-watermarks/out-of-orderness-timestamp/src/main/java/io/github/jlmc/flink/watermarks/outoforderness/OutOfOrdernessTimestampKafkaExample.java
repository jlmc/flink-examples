package io.github.jlmc.flink.watermarks.outoforderness;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.Instant;

public class OutOfOrdernessTimestampKafkaExample {

    public static void main(String[] args) throws Exception {
        String bootstrapServers = "kafka:19092";
        String inputTopic = "sensors-data";
        String outputTopic = "sensors-avg-data";

        for (int i = 0; i < args.length; i++) {
            if ("--bootstrap.servers".equals(args[i]) && i + 1 < args.length) {
                bootstrapServers = args[++i];
            } else if ("--input.topic".equals(args[i]) && i + 1 < args.length) {
                inputTopic = args[++i];
            } else if ("--output.topic".equals(args[i]) && i + 1 < args.length) {
                outputTopic = args[++i];
            }
        }

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<SensorReading> source = KafkaSource.<SensorReading>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("out-of-orderness-timestamp-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(SensorReading.class))
                .build();

        DataStream<SensorReading> sensorStream = env.fromSource(source, createWatermarkStrategy(), "Kafka Source");

        DataStream<WindowResult> resultStream = definePipeline(sensorStream);

        KafkaSink<WindowResult> sink = KafkaSink.<WindowResult>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<WindowResult>builder()
                                .setTopic(outputTopic)
                                .setValueSerializationSchema(new JsonSerializationSchema<>())
                                .build())
                .build();

        resultStream.sinkTo(sink).name("Kafka Sink");

        resultStream
                .map(WindowResult::toString)
                .print("WINDOW_RESULT");

        env.execute("Out Of Orderness Timestamp Kafka Example");
    }

    public static WatermarkStrategy<SensorReading> createWatermarkStrategy() {
        return WatermarkStrategy
                .<SensorReading>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                .withTimestampAssigner((SerializableTimestampAssigner<SensorReading>) (event, recordTimestamp) -> event.timestamp.toEpochMilli())
                .withIdleness(Duration.ofMinutes(1));
    }

    public static DataStream<WindowResult> definePipeline(DataStream<SensorReading> sensorStream) {
        return sensorStream
                .keyBy(r -> r.id)
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                .aggregate(
                        new SensorReadingAverageAggregateFunction(),
                        new AverageAccumulatorWindowResultProcessWindowFunction()
                );
    }

    public static class AverageAccumulator implements java.io.Serializable {
        long count;
        double sum;

        public AverageAccumulator() {
            this.count = 0L;
            this.sum = 0.0;
        }

        public AverageAccumulator add(double value) {
            this.count++;
            this.sum += value;
            return this;
        }

        public double average() {
            return count == 0 ? 0.0 : sum / count;
        }
    }

    public static class SensorReadingAverageAggregateFunction implements AggregateFunction<SensorReading, AverageAccumulator, AverageAccumulator> {
        @Override
        public AverageAccumulator createAccumulator() {
            return new AverageAccumulator();
        }

        @Override
        public AverageAccumulator add(SensorReading value, AverageAccumulator accumulator) {
            return accumulator.add(value.temperature);
        }

        @Override
        public AverageAccumulator getResult(AverageAccumulator accumulator) {
            return accumulator;
        }

        @Override
        public AverageAccumulator merge(AverageAccumulator a, AverageAccumulator b) {
            AverageAccumulator merged = new AverageAccumulator();
            merged.count = a.count + b.count;
            merged.sum = a.sum + b.sum;
            return merged;
        }
    }

    public static class AverageAccumulatorWindowResultProcessWindowFunction
            extends ProcessWindowFunction<AverageAccumulator, WindowResult, String, TimeWindow> {
        @Override
        public void process(String key,
                            ProcessWindowFunction<AverageAccumulator, WindowResult, String, TimeWindow>.Context context,
                            Iterable<AverageAccumulator> elements,
                            Collector<WindowResult> out) {
            AverageAccumulator acc = elements.iterator().next();
            out.collect(new WindowResult(
                    key,
                    acc.average(),
                    acc.count,
                    Instant.ofEpochMilli(context.window().getStart()),
                    Instant.ofEpochMilli(context.window().getEnd())
            ));
        }
    }
}
