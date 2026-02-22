package io.github.jlmc.flink.exchange;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.ExecutionOptions;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.Serializable;

/**
 * Global Partitioner Example.
 * This example demonstrates how to use `.global()` to send all elements
 * from the upstream source to a single, specific instance of the downstream
 * operator (subtask index 0).
 */
public class GlobalPartitionerExample {

    public static void main(String[] args) throws Exception {
        execute(args);
    }

    public static void execute(String[] args) throws Exception {
        String brokers = System.getProperty("brokers", "localhost:9092");
        String inputTopic = System.getProperty("input-topic", "distributed-events");
        String outputTopic = System.getProperty("output-topic", "global-summary");

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Source with multiple partitions to simulate distributed data
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(brokers)
                .setTopics(inputTopic)
                .setGroupId("global-partitioner-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> events = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Distributed Source")
                .setParallelism(4); // Upstream has 4 parallel workers

        // 2. Funnel all data into a single subtask using .global()
        DataStream<Result> globalStream = events
                .global()                              // All records go to subtask 0
                .map(new GlobalAggregationFunction())  // Processes all records centrally
                .setParallelism(4);                    // Even with parallelism 4, only subtask 0 is used

        globalStream.print();

        // 3. Sink the results to Kafka
        KafkaSink<Result> sink = KafkaSink.<Result>builder()
                .setBootstrapServers(brokers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(outputTopic)
                        .setValueSerializationSchema(new JsonSerializationSchema<Result>())
                        .build())
                .build();

        globalStream.sinkTo(sink);

        // 4. Handle Execution Mode
        if (env.getConfiguration().get(ExecutionOptions.RUNTIME_MODE) == RuntimeExecutionMode.BATCH) {
            env.execute("Global Partitioning Example");
        } else {
            env.executeAsync("Global Partitioning Example");
        }
    }

    public record Result(String value, int subtaskIndex) implements Serializable {}

    /**
     * A function that processes elements centrally.
     */
    public static class GlobalAggregationFunction extends RichMapFunction<String, Result> {
        @Override
        public Result map(String value) {
            int subtaskIndex = getRuntimeContext().getTaskInfo().getIndexOfThisSubtask();
            return new Result(value, subtaskIndex);
        }
    }
}
