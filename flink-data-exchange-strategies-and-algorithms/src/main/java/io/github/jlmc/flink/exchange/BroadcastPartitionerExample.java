package io.github.jlmc.flink.exchange;

import org.apache.flink.api.common.RuntimeExecutionMode; // To choose execute vs executeAsync
import org.apache.flink.api.common.eventtime.WatermarkStrategy; // No event-time semantics required here
import org.apache.flink.api.common.functions.OpenContext; // Lifecycle hook
import org.apache.flink.api.common.serialization.SimpleStringSchema; // Kafka String SerDe
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema; // Kafka sink serializer builder
import org.apache.flink.connector.kafka.sink.KafkaSink; // Kafka sink
import org.apache.flink.connector.kafka.source.KafkaSource; // Kafka source
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer; // Start offsets policy
import org.apache.flink.streaming.api.datastream.DataStream; // DataStream API
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment; // Env
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction; // Rich co flatMap to access runtime context
import org.apache.flink.util.Collector; // Collect downstream results

import java.io.Serializable; // For record type
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
        String configTopic = System.getProperty("config-topic", "thresholds"); // Small config stream
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

        // Config source (e.g., updated threshold values)
        KafkaSource<String> configSource = KafkaSource.<String>builder() // Build Kafka source for config
                .setBootstrapServers(brokers) // Brokers
                .setTopics(configTopic) // Config topic
                .setGroupId("broadcast-config-group") // Separate group id
                .setStartingOffsets(OffsetsInitializer.earliest()) // Deterministic in tests
                .setValueOnlyDeserializer(new SimpleStringSchema()) // Value as String
                .build(); // Finish

        // Create data streams from both sources
        DataStream<String> dataStream = env.fromSource(dataSource, WatermarkStrategy.noWatermarks(), "Kafka Main Data"); // Data stream
        DataStream<String> configStream = env.fromSource(configSource, WatermarkStrategy.noWatermarks(), "Kafka Config"); // Config stream

        // Define the workflow that broadcasts the config and connects to the data stream
        DataStream<Alert> alerts = defineWorkflow(dataStream, configStream); // Apply processing

        // Optional: print to local console for inspection
        alerts.print(); // Debug visibility

        // Create a Kafka sink to write alerts as strings
        KafkaSink<String> sink = KafkaSink.<String>builder() // Start sink builder
                .setBootstrapServers(brokers) // Brokers
                .setRecordSerializer(KafkaRecordSerializationSchema.builder() // Serializer builder
                        .setTopic(outputTopic) // Alerts topic
                        .setValueSerializationSchema(new SimpleStringSchema()) // String values
                        .build()) // Finish serializer
                .build(); // Build sink

        // Convert Alert to string and sink to Kafka
        alerts.map(Alert::toString) // Serialize alert
                .sinkTo(sink); // Send to Kafka

        // Execute according to runtime mode
        if (env.getConfiguration().get(org.apache.flink.configuration.ExecutionOptions.RUNTIME_MODE) == RuntimeExecutionMode.BATCH) { // Batch?
            env.execute("Broadcast Partitioner Example"); // Blocking
        } else {
            env.executeAsync("Broadcast Partitioner Example"); // Non-blocking
        }
    }

    // Workflow: broadcast config to all subtasks and combine with main data
    public static DataStream<Alert> defineWorkflow(DataStream<String> data, DataStream<String> config) { // Define transformation
        return data // Start with data
                .rebalance() // Ensure data is distributed across multiple downstream subtasks for the demo
                .connect(config.broadcast()) // Broadcast config so ALL subtasks receive it
                .flatMap(new ThresholdJoinFunction()) // Combine data with latest threshold per subtask
                .setParallelism(4); // Make the connected operator run with multiple parallel instances
    }

    // Output record with subtask info for verification
    public record Alert(String data, String threshold, int subtaskIndex) implements Serializable { // Simple record
        @Override public String toString() { // JSON-ish output for easy parsing
            return "{\"data\": \"" + data + "\", \"threshold\": \"" + threshold + "\", \"subtaskIndex\": " + subtaskIndex + "}"; // Compact
        }
    }

    // Rich co-flatMap that stores and applies the latest threshold seen by this subtask
    public static class ThresholdJoinFunction extends RichCoFlatMapFunction<String, String, Alert> { // Rich variant to access subtask index
        private transient AtomicReference<String> latestThreshold; // Holds latest threshold for this subtask
        private transient int subtask; // Cached subtask index

        @Override public void open(OpenContext openContext) { // Initialize on TM
            this.latestThreshold = new AtomicReference<>("N/A"); // Default threshold before any config arrives
            this.subtask = getRuntimeContext().getTaskInfo().getIndexOfThisSubtask(); // Capture subtask id
        }

        @Override public void flatMap1(String value, Collector<Alert> out) { // Called for data stream elements
            String threshold = latestThreshold.get(); // Read latest threshold for this subtask
            out.collect(new Alert(value, threshold, subtask)); // Emit alert enriched with threshold and subtask id
        }

        @Override public void flatMap2(String thresholdValue, Collector<Alert> out) { // Called for broadcasted config updates
            latestThreshold.set(thresholdValue); // Update threshold for all subsequent data elements on this subtask
            // We don't emit on config-only updates; effect is visible on next data arrival
        }
    }
}
