package io.github.jlmc.flink.exchange;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.jackson.JacksonMapperFactory;

import java.io.Serializable;

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

        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(brokers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(outputTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        errorEvents.map(UserEvent::toString)
                .sinkTo(sink);

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

    public record UserEvent(String level, String message, String userId) implements Serializable {}

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
