package io.github.jlmc.flink.exchange;

import com.fasterxml.jackson.databind.ObjectMapper; // Standard Jackson mapper
import org.apache.flink.api.common.RuntimeExecutionMode; // To choose execute vs executeAsync
import org.apache.flink.api.common.eventtime.WatermarkStrategy; // No event-time semantics required here
import org.apache.flink.api.common.functions.OpenContext; // Lifecycle hook
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema; // Kafka String SerDe
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema; // Kafka sink serializer builder
import org.apache.flink.connector.kafka.sink.KafkaSink; // Kafka sink
import org.apache.flink.connector.kafka.source.KafkaSource; // Kafka source
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer; // Start offsets policy
import org.apache.flink.connector.kafka.util.JacksonMapperFactory;
import org.apache.flink.streaming.api.datastream.DataStream; // DataStream API
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment; // Env
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction; // Rich co flatMap to access runtime context
import org.apache.flink.util.Collector; // Collect downstream results

import java.io.Serializable; // For record type
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference; // Simple holder for latest config per subtask

/**
 * Broadcast Partitioner example where a small configuration stream is broadcast to all workers
 * and connected with a main high-volume stream. Every line is documented.
 */
public class BroadcastPartitionerExample {

    // Main entry point
    public static void main(String[] args) throws Exception { // Standard main
        execute(args); // Delegate to execute for testability
    }

    // Build and run the job
    public static void execute(String[] args) throws Exception { // Orchestrates the pipeline
        String brokers = System.getProperty("brokers", "localhost:9092"); // Kafka brokers
        String dataTopic = System.getProperty("data-topic", "transactions"); // High-volume main stream
        String configTopic = System.getProperty("config-topic", "rules"); // Small config stream
        String outputTopic = System.getProperty("output-topic", "alerts"); // Alerts sink

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(); // Obtain env

        // Main data source (e.g., transactions)
        KafkaSource<String> dataSource = KafkaSource.<String>builder() // Build Kafka source for data
                .setBootstrapServers(brokers) // Brokers
                .setTopics(dataTopic) // Data topic
                .setGroupId("broadcast-data-group") // Group id
                .setStartingOffsets(OffsetsInitializer.earliest()) // Deterministic in tests
                .setValueOnlyDeserializer(new SimpleStringSchema()) // Value as String
                .build(); // Finish

        // Config source (e.g., dynamic rules)
        KafkaSource<String> configSource = KafkaSource.<String>builder() // Build Kafka source for rules
                .setBootstrapServers(brokers) // Brokers
                .setTopics(configTopic) // Config topic
                .setGroupId("broadcast-config-group") // Separate group id
                .setStartingOffsets(OffsetsInitializer.earliest()) // Deterministic in tests
                .setValueOnlyDeserializer(new SimpleStringSchema()) // Value as String
                .build(); // Finish

        // Create data streams from both sources
        DataStream<String> dataStream = env.fromSource(dataSource, WatermarkStrategy.noWatermarks(), "Kafka Transactions"); // Data stream
        DataStream<String> configStream = env.fromSource(configSource, WatermarkStrategy.noWatermarks(), "Kafka Rules"); // Config stream

        // Define the workflow that broadcasts the config and connects to the data stream
        DataStream<Alert> alerts = defineWorkflow(dataStream, configStream); // Apply processing

        // Optional: print to local console for inspection
        alerts.print(); // Debug visibility

        // Create a Kafka sink to write alerts as strings using Jackson
        KafkaSink<Alert> sink = KafkaSink.<Alert>builder() // Start sink builder
                .setBootstrapServers(brokers) // Brokers
                .setRecordSerializer(KafkaRecordSerializationSchema.builder() // Serializer builder
                        .setTopic(outputTopic) // Alerts topic
                        .setValueSerializationSchema(new JacksonSerializationSchema<>(Alert.class)) // Jackson serialization
                        .build()) // Finish serializer
                .build(); // Build sink

        // Sink alerts to Kafka
        alerts.sinkTo(sink); // Send to Kafka

        // Execute according to runtime mode
        if (env.getConfiguration().get(org.apache.flink.configuration.ExecutionOptions.RUNTIME_MODE) == RuntimeExecutionMode.BATCH) { // Batch?
            env.execute("Broadcast Partitioner Example"); // Blocking
        } else {
            env.executeAsync("Broadcast Partitioner Example"); // Non-blocking
        }
    }

    // Workflow: broadcast rules to all subtasks and combine with transactions
    public static DataStream<Alert> defineWorkflow(DataStream<String> transactions, DataStream<String> rules) { // Define transformation
        return transactions // Start with transactions
                .rebalance() // Ensure data is distributed across multiple downstream subtasks for the demo
                .connect(rules.broadcast()) // Broadcast rules so ALL subtasks receive them
                .flatMap(new FraudDetectorFunction()) // Combine transactions with latest rule per subtask
                .setParallelism(4); // Make the connected operator run with multiple parallel instances
    }

    // Complex types for the example
    public record Transaction(String id, double amount) implements Serializable {}
    public record Rule(String type, double threshold) implements Serializable {}

    // Output record with subtask info for verification
    public record Alert(Transaction transaction, Rule rule, int subtaskIndex) implements Serializable { // Simple record
        @Override public String toString() { // JSON-ish output for easy parsing
            return String.format("{\"transaction\": {\"id\": \"%s\", \"amount\": %.2f}, \"rule\": {\"type\": \"%s\", \"threshold\": %.2f}, \"subtaskIndex\": %d}",
                    transaction.id(), transaction.amount(), rule.type(), rule.threshold(), subtaskIndex);
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

    // Rich co-flatMap that stores and applies the latest rule seen by this subtask
    public static class FraudDetectorFunction extends RichCoFlatMapFunction<String, String, Alert> { // Rich variant to access subtask index
        private transient AtomicReference<Rule> latestRule; // Holds latest rule for this subtask
        private transient int subtask; // Cached subtask index
        private transient ObjectMapper mapper; // For JSON parsing

        @Override public void open(OpenContext openContext) { // Initialize on TM
            this.latestRule = new AtomicReference<>(new Rule("DEFAULT", 1000.0)); // Default rule
            this.subtask = getRuntimeContext().getTaskInfo().getIndexOfThisSubtask(); // Capture subtask id
            this.mapper = JacksonMapperFactory.createObjectMapper();
        }

        @Override public void flatMap1(String value, Collector<Alert> out) throws Exception { // Called for transactions stream
            Transaction transaction = mapper.readValue(value, Transaction.class);
            Rule rule = latestRule.get(); // Read latest rule for this subtask
            if (transaction.amount() > rule.threshold()) {
                out.collect(new Alert(transaction, rule, subtask)); // Emit alert enriched with rule and subtask id
            }
        }

        @Override public void flatMap2(String ruleValue, Collector<Alert> out) throws Exception { // Called for broadcasted rules
            Rule rule = mapper.readValue(ruleValue, Rule.class);
            latestRule.set(rule); // Update rule for all subsequent transactions on this subtask
        }
    }
}
