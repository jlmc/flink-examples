package io.github.jlmc.flink.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.ExecutionOptions;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.util.JacksonMapperFactory;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Shuffle Partitioner Example.
 *
 * This example demonstrates how to use `.shuffle()` to distribute a heavy workload
 * from a low-parallelism source (like a single Kafka partition or a single file)
 * across multiple parallel workers.
 */
public class ShufflePartitionerExample {

    public static void main(String[] args) throws Exception {
        execute(args);
    }

    public static void execute(String[] args) throws Exception {
        String brokers = System.getProperty("brokers", "localhost:9092");
        String inputTopic = System.getProperty("input-topic", "heavy-logs");
        String outputTopic = System.getProperty("output-topic", "processed-results");

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Source with low parallelism (e.g., a single partition Kafka topic)
        // In this example, we'll use a KafkaSource. 
        // If the topic has only 1 partition, the source parallelism will effectively be 1.
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(brokers)
                .setTopics(inputTopic)
                .setGroupId("shuffle-partitioner-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> heavyLogStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Single Partition Source")
                .setParallelism(1);

        // 2. Distribute the heavy load across the cluster using .shuffle()
        DataStream<Result> processedStream = heavyLogStream
                .shuffle()                             // Randomly sends data to all available workers
                .map(new HeavyComputeFunction())       // A CPU-intensive operation
                .setParallelism(4);                    // Scale up to 4 workers (in a real scenario, this would be higher)

        processedStream.print();

        // 3. Sink the results to Kafka using Jackson
        KafkaSink<Result> sink = KafkaSink.<Result>builder()
                .setBootstrapServers(brokers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(outputTopic)
                        .setValueSerializationSchema(new JacksonSerializationSchema<>(Result.class))
                        .build())
                .build();

        processedStream.sinkTo(sink);

        // 4. Handle Execution Mode
        if (env.getConfiguration().get(ExecutionOptions.RUNTIME_MODE) == RuntimeExecutionMode.BATCH) {
            env.execute("Shuffle Partitioning Example");
        } else {
            env.executeAsync("Shuffle Partitioning Example");
        }
    }

    public record Result(String original, String processed, int subtaskIndex) implements Serializable {
        @Override
        public String toString() {
            return String.format("""
                    {"original": "%s", "processed": "%s", "subtaskIndex": %d}""", original, processed, subtaskIndex);
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

    /**
     * A simulated CPU-intensive operation.
     */
    public static class HeavyComputeFunction extends RichMapFunction<String, Result> {
        private transient ObjectMapper mapper;

        @Override
        public void open(OpenContext openContext) {
            this.mapper = JacksonMapperFactory.createObjectMapper();
        }

        @Override
        public Result map(String value) throws Exception {
            // Simulate heavy computation
            // In a real scenario, this might be complex parsing, encryption, or heavy math
            String processed = value.toUpperCase();
            
            // Artificial delay to emphasize the need for parallelism
            Thread.sleep(100); 

            int subtaskIndex = getRuntimeContext().getTaskInfo().getIndexOfThisSubtask();
            return new Result(value, processed, subtaskIndex);
        }
    }
}
