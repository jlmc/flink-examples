package io.github.jlmc.flink.windows.tumbling;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.Instant;

/**
 * Real-world example using Tumbling Windows to calculate the average temperature per sensor every 10 seconds.
 * Consumes JSON events from Kafka.
 */
public class TumblingWindowKafkaExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        ParameterTool parameters = ParameterTool.fromArgs(args);
        String bootstrapServers = parameters.get("bootstrap.servers", "localhost:9092");
        String inputTopic = parameters.get("input.topic", "sensors-data");
        String outputTopic = parameters.get("output.topic", "sensors-avg-data");

        // Kafka Source setup
        KafkaSource<SensorReading> source = KafkaSource.<SensorReading>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("tumbling-windows-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(
                        SensorReading.class,
                        () -> {
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(new JavaTimeModule());
                            return objectMapper;
                        }
                ))
                .build();

        // Kafka Sink setup
        JsonSerializationSchema<WindowResult> serializationSchema = new JsonSerializationSchema<>(
                () -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.registerModule(new JavaTimeModule());
                    return objectMapper;
                }
        );
        KafkaSink<WindowResult> sink = KafkaSink.<WindowResult>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(outputTopic)
                                .setKeySerializationSchema(new SerializationSchema<WindowResult>() {
                                    @Override
                                    public byte[] serialize(WindowResult element) {
                                        return element.sensorId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                    }
                                })
                                .setValueSerializationSchema(serializationSchema)
                                .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        execute(env, source, sink);
    }

    public static void execute(StreamExecutionEnvironment env,
                               org.apache.flink.api.connector.source.Source<SensorReading, ?, ?> source,
                               org.apache.flink.api.connector.sink2.Sink<WindowResult> sink) throws Exception {

        DataStream<SensorReading> sensorStream = env.fromSource(source, createWatermarkStrategy(), "Sensor Source");

        definePipeline(sensorStream).sinkTo(sink);

        env.execute("Flink Tumbling Window Example");
    }

    public static WatermarkStrategy<SensorReading> createWatermarkStrategy() {
        return WatermarkStrategy
                .<SensorReading>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                .withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli());
    }

    public static DataStream<WindowResult> definePipeline(DataStream<SensorReading> input) {
        // Apply 10-second Tumbling Window per sensor ID
        return input
                .keyBy((SensorReading r) -> r.id)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(10)))
                .aggregate(
                        new SensorReadingAverageAccumulatorAggregateFunction(),
                        new AverageAccumulatorWindowResultStringTimeWindowProcessWindowFunction()
                );
    }

    public static class SensorReadingAverageAccumulatorAggregateFunction implements AggregateFunction<SensorReading, AverageAccumulator, AverageAccumulator> {

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
            return AverageAccumulator.merge(a, b);
        }
    }

    public static class AverageAccumulatorWindowResultStringTimeWindowProcessWindowFunction extends ProcessWindowFunction<AverageAccumulator, WindowResult, String, TimeWindow> {

        @Override
        public void open(OpenContext openContext) throws Exception {
            super.open(openContext);
        }

        @Override
        public void process(String id,
                            ProcessWindowFunction<AverageAccumulator, WindowResult, String, TimeWindow>.Context context,
                            Iterable<AverageAccumulator> elements,
                            Collector<WindowResult> out) {

            // Extraímos o acumulador final
            AverageAccumulator finalAccumulator = elements.iterator().next();

            out.collect(new WindowResult(
                    id,
                    finalAccumulator.getAverage(),
                    finalAccumulator.getCount(), // Fiabilidade: nº de medições
                    Instant.ofEpochMilli(context.window().getStart()),
                    Instant.ofEpochMilli(context.window().getEnd())
            ));

        }
    }
}
