package io.github.jlmc.flink.windows.sliding;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
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
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.Instant;

/**
 * Real-world example using Sliding Windows for Access Security detection.
 * If a user fails the password 5 times in 30 seconds, it's likely a bot.
 * We use a sliding window of 30s that slides every 5s.
 */
public class SlidingWindowKafkaExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        ParameterTool parameters = ParameterTool.fromArgs(args);
        String bootstrapServers = parameters.get("bootstrap.servers", "kafka:19092");
        String inputTopic = parameters.get("input.topic", "access-attempts");
        String outputTopic = parameters.get("output.topic", "access-alerts");

        KafkaSource<AccessEvent> source = KafkaSource.<AccessEvent>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("sliding-windows-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(
                        AccessEvent.class,
                        () -> {
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(new JavaTimeModule());
                            return objectMapper;
                        }
                ))
                .build();

        JsonSerializationSchema<AccessAlert> serializationSchema = new JsonSerializationSchema<>(
                () -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.registerModule(new JavaTimeModule());
                    return objectMapper;
                }
        );

        KafkaSink<AccessAlert> sink = KafkaSink.<AccessAlert>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(outputTopic)
                                .setKeySerializationSchema(new SerializationSchema<AccessAlert>() {
                                    @Override
                                    public byte[] serialize(AccessAlert element) {
                                        return element.userId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
                               org.apache.flink.api.connector.source.Source<AccessEvent, ?, ?> source,
                               org.apache.flink.api.connector.sink2.Sink<AccessAlert> sink) throws Exception {

        DataStream<AccessEvent> accessStream = env.fromSource(source, createWatermarkStrategy(), "Access Source");

        definePipeline(accessStream).sinkTo(sink);

        env.execute("Flink Sliding Window Access Security Example");
    }

    public static WatermarkStrategy<AccessEvent> createWatermarkStrategy() {
        return WatermarkStrategy
                .<AccessEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                .withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli());
    }

    public static DataStream<AccessAlert> definePipeline(DataStream<AccessEvent> accessStream) {
        // Sliding window of 30s that "slides" every 5s
        return accessStream
                .filter(event -> !event.success) // Only failed attempts
                .keyBy(event -> event.userId)
                .window(SlidingEventTimeWindows.of(Duration.ofSeconds(30), Duration.ofSeconds(5)))
                .process(new BotDetectionProcessFunction())
                .filter(alert -> alert.failedAttempts >= 5);
    }

    public static class BotDetectionProcessFunction extends ProcessWindowFunction<AccessEvent, AccessAlert, String, TimeWindow> {
        @Override
        public void process(String userId,
                            Context context,
                            Iterable<AccessEvent> elements,
                            Collector<AccessAlert> out) {
            long count = 0;
            for (AccessEvent ignored : elements) {
                count++;
            }

            out.collect(new AccessAlert(
                    userId,
                    count,
                    Instant.ofEpochMilli(context.window().getStart()),
                    Instant.ofEpochMilli(context.window().getEnd())
            ));
        }
    }
}
