package io.github.jlmc.flink.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.RuntimeExecutionMode; // Used to decide sync/async execution
import org.apache.flink.api.common.eventtime.WatermarkStrategy; // No watermarks needed for this simple example
import org.apache.flink.api.common.functions.OpenContext; // Lifecycle hook for Rich functions
import org.apache.flink.api.common.functions.RichMapFunction; // To access subtask index for demonstration
import org.apache.flink.api.common.serialization.SimpleStringSchema; // Kafka String serializer/deserializer
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema; // To build sink to Kafka
import org.apache.flink.connector.kafka.sink.KafkaSink; // Kafka sink
import org.apache.flink.connector.kafka.source.KafkaSource; // Kafka source
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer; // Start at earliest for tests
import org.apache.flink.connector.kafka.util.JacksonMapperFactory;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream; // Core Flink DataStream API
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment; // Flink env

import java.io.Serializable; // For the record type
import java.nio.charset.StandardCharsets;

/**
 * Rebalance Partitioner example demonstrating deterministic round-robin distribution.
 * Every line is documented to explain its purpose.
 */
public class RebalancePartitionerExample {

    // Entry point for running from CLI or tests
    public static void main(String[] args) throws Exception { // Standard Java main signature
        execute(args); // Delegate to execute() to make tests easier to call
    }

    // Builds and runs the job. Reads from Kafka, rebalances, processes, and writes back to Kafka
    public static void execute(String[] args) throws Exception { // Exposed for ITs
        String brokers = System.getProperty("brokers", "localhost:9092"); // Kafka bootstrap servers (overridable)
        String inputTopic = System.getProperty("input-topic", "skewed-logs"); // Source topic name
        String outputTopic = System.getProperty("output-topic", "balanced-results"); // Sink topic name

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(); // Create env (inherits mode from tests)

        // Configure a Kafka source that reads raw strings and starts from earliest for reproducibility
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder() // Start KafkaSource builder
                .setBootstrapServers(brokers) // Kafka address
                .setTopics(inputTopic) // Topic to read from
                .setGroupId("rebalance-partitioner-group") // Consumer group
                .setStartingOffsets(OffsetsInitializer.earliest()) // Deterministic consumption for tests
                .setValueOnlyDeserializer(new SimpleStringSchema()) // Read value as UTF-8 String
                .build(); // Build the source

        // Ingest Kafka source as a DataStream with no watermarks (event-time not relevant here)
        DataStream<String> rawStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Skewed Logs"); // Source operator

        // Define the processing workflow with a rebalance step
        DataStream<LogEvent> processed = defineWorkflow(rawStream); // Apply transformation chain

        // Print for local visibility (optional; handy during dev)
        processed.print(); // Side-effect: logs to stdout

        // Create a Kafka sink that writes the JSON-ified LogEvent output using Jackson
        KafkaSink<LogEvent> sink = KafkaSink.<LogEvent>builder() // Start KafkaSink builder
                .setBootstrapServers(brokers) // Kafka address

                .setRecordSerializer(KafkaRecordSerializationSchema.builder() // Build record serializer
                        .setTopic(outputTopic) // Target topic
                        .setValueSerializationSchema(new JsonSerializationSchema<LogEvent>()) // Jackson serialization
                        .setKeySerializationSchema(logEvent -> logEvent.id() != null ? logEvent.id().getBytes(StandardCharsets.UTF_8) : new byte[0]) // Use id as key if present
                        .build()) // Finish record serializer
                .build(); // Build the sink

        // Send to Kafka
        processed.sinkTo(sink); // Connect to Kafka sink

        // Execute synchronously in batch, asynchronously otherwise (so ITs don't block)
        if (env.getConfiguration().get(org.apache.flink.configuration.ExecutionOptions.RUNTIME_MODE) == RuntimeExecutionMode.BATCH) { // Check runtime
            env.execute("Rebalance Partitioning Example"); // Blocking execute in batch
        } else {
            env.executeAsync("Rebalance Partitioning Example"); // Non-blocking in streaming
        }
    }

    // Core workflow demonstrating .rebalance() to force round-robin distribution across downstream subtasks
    public static DataStream<LogEvent> defineWorkflow(DataStream<String> input) { // Transformation definition
        return input // Start with raw input lines
                .rebalance() // Force round-robin distribution to evenly spread load
                .map(new JsonToLogEventParser()); // Map each element and record which subtask processed it
    }

    // Simple record type capturing original payload and processing subtask index
    public record LogEvent(String id,  String level, String message, int subtaskIndex) implements Serializable { // Java 17 record with Serializable
    }

    // Rich function to access subtask index and attach it to each processed element
    public static class JsonToLogEventParser extends RichMapFunction<String, LogEvent> { // RichMap to access runtime context
        private transient int subtask; // Cached subtask index (transient: not serialized)
        private transient ObjectMapper mapper;

        @Override public void open(OpenContext openContext) { // Initialize on TM side
            this.subtask = getRuntimeContext().getTaskInfo().getIndexOfThisSubtask(); // Obtain subtask id (0..parallelism-1)
            this.mapper = JacksonMapperFactory.createObjectMapper();
        }

        @Override public LogEvent map(String value) throws Exception { // Map each input value
            LogEvent event = mapper.readValue(value, LogEvent.class);
            return new LogEvent(event.id(), event.level(), event.message(), subtask); // Tag with subtask index
        }
    }
}
