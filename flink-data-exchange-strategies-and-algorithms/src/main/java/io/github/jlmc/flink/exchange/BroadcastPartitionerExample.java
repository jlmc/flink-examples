package io.github.jlmc.flink.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.util.JacksonMapperFactory;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

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

                .setRecordSerializer(new AlertSerializationSchema(outputTopic)) // Finish serializer

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
    public record Rule(String id, String type, double threshold) implements Serializable {}

    // Output record with subtask info for verification
    public record Alert(String id, Transaction transaction, Rule rule, int subtaskIndex) implements Serializable { // Simple record
    }

    // Generic Jackson Serialization Schema
    public static class AlertSerializationSchema implements KafkaRecordSerializationSchema<Alert> {
        private final String topic;
        private transient ObjectMapper mapper;

        public AlertSerializationSchema(String topic) {
            this.topic = topic;
        }

        @Override
        public void open(SerializationSchema.InitializationContext context, KafkaSinkContext sinkContext) throws Exception {
            this.mapper = JacksonMapperFactory.createObjectMapper();
        }

        @Override
        public org.apache.kafka.clients.producer.ProducerRecord<byte[], byte[]> serialize(Alert element, KafkaSinkContext context, Long timestamp) {
            try {
                byte[] key = element.id().getBytes(StandardCharsets.UTF_8);
                byte[] value = mapper.writeValueAsBytes(element);
                return new org.apache.kafka.clients.producer.ProducerRecord<>(topic, key, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Rich co-flatMap that stores and applies the latest rule seen by this subtask
    public static class FraudDetectorFunction extends RichCoFlatMapFunction<String, String, Alert> { // Rich variant to access subtask index
        private transient java.util.Map<String, Rule> rulesRepository; // Holds rules for this subtask
        private transient int subtask; // Cached subtask index
        private transient ObjectMapper mapper; // For JSON parsing

        @Override public void open(OpenContext openContext) { // Initialize on TM
            this.rulesRepository = new java.util.concurrent.ConcurrentHashMap<>();
            this.rulesRepository.put("DEFAULT", new Rule("DEFAULT", "DEFAULT", 1000.0)); // Default rule
            this.subtask = getRuntimeContext().getTaskInfo().getIndexOfThisSubtask(); // Capture subtask id
            this.mapper = JacksonMapperFactory.createObjectMapper();
        }

        @Override public void flatMap1(String value, Collector<Alert> out) throws Exception { // Called for transactions stream
            Transaction transaction = mapper.readValue(value, Transaction.class);
            for (Rule rule : rulesRepository.values()) {
                if (transaction.amount() > rule.threshold()) {
                    String alertId = transaction.id() + "-" + rule.id();
                    out.collect(new Alert(alertId, transaction, rule, subtask)); // Emit alert enriched with rule and subtask id
                }
            }
        }

        @Override public void flatMap2(String ruleValue, Collector<Alert> out) throws Exception { // Called for broadcasted rules
            Rule rule = mapper.readValue(ruleValue, Rule.class);
            rulesRepository.put(rule.id(), rule); // Update repository for all subsequent transactions on this subtask
        }
    }
}
