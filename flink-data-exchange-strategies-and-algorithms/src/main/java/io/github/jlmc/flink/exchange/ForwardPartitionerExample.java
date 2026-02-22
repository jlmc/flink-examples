package io.github.jlmc.flink.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.util.JacksonMapperFactory;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class ForwardPartitionerExample {

    public static void main(String[] args) throws Exception {
        execute(args);
    }

    public static void execute(String[] args) throws Exception {
        String brokers = System.getProperty("brokers", "localhost:9092");
        String inputTopic = System.getProperty("input-topic", "logs");
        String outputTopic = System.getProperty("output-topic", "error-logs");

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(brokers)
                .setTopics(inputTopic)
                .setGroupId("forward-partitioner-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> rawLogStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Logs");

        DataStream<UserEvent> errorEvents = defineWorkflow(rawLogStream);

        errorEvents.print();

        KafkaSink<UserEvent> sink = KafkaSink.<UserEvent>builder()
                .setBootstrapServers(brokers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(outputTopic)
                        .setValueSerializationSchema(new JacksonSerializationSchema<>(UserEvent.class))
                        .build())
                .build();

        errorEvents.sinkTo(sink);

        if (env.getConfiguration().get(org.apache.flink.configuration.ExecutionOptions.RUNTIME_MODE) == RuntimeExecutionMode.BATCH) {
            env.execute("Forward Partitioning Example");
        } else {
            env.executeAsync("Forward Partitioning Example");
        }
    }

    public static DataStream<UserEvent> defineWorkflow(DataStream<String> rawLogStream) {
        return rawLogStream
                .filter(log -> log.contains("ERROR"))
                .forward()
                .map(new JsonToEventParser());
    }

    public record UserEvent(String level, String message, String userId) implements Serializable {
        @Override
        public String toString() {
            return String.format("{\"level\": \"%s\", \"message\": \"%s\", \"userId\": \"%s\"}", level, message, userId);
        }
    }

    // Generic Jackson Serialization Schema
    public static class JacksonSerializationSchema<T> implements SerializationSchema<T> {
        private transient ObjectMapper mapper;
        private final Class<T> clazz;

        public JacksonSerializationSchema(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override public void open(InitializationContext context) {
            this.mapper = JacksonMapperFactory.createObjectMapper();
        }

        @Override public byte[] serialize(T element) {
            try {
                return mapper.writeValueAsString(element).getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                return new byte[0];
            }
        }
    }

    public static class JsonToEventParser extends RichMapFunction<String, UserEvent> {
        private transient ObjectMapper mapper;

        @Override
        public void open(OpenContext openContext) {
            this.mapper = JacksonMapperFactory.createObjectMapper();
        }

        @Override
        public UserEvent map(String value) throws Exception {
            return mapper.readValue(value, UserEvent.class);
        }

        @Override
        public void close() throws Exception {
            super.close();
        }
    }
}
