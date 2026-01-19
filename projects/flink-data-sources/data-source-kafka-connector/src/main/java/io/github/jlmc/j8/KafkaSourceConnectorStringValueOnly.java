package io.github.jlmc.j8;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceOptions;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaSourceConnectorStringValueOnly {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSourceConnectorStringValueOnly.class);
    
    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        // Set the web UI port to 8081 (or another free port) to avoid conflicts if multiple jobs are running
        conf.set(RestOptions.PORT, 8081);
        conf.set(TaskManagerOptions.NUM_TASK_SLOTS, 4);

        @SuppressWarnings("resource")
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);
        LOGGER.info("Hello, Kafka Source Connector with String Value Only!");

        env.enableCheckpointing(3000, CheckpointingMode.EXACTLY_ONCE);
        KafkaSource<String> kafkaSource =
                KafkaSource.<String>builder()
                        .setBootstrapServers("localhost:9092")
                        .setTopics("my-data-stream")
                        .setGroupId("KafkaSourceConnectorStringValueOnly")
                        // configure other options as needed
                        .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                        .setValueOnlyDeserializer(new SimpleStringSchema())
                        .setProperty(KafkaSourceOptions.COMMIT_OFFSETS_ON_CHECKPOINT.key(), "true")
                        .setProperty(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
                        .build();



        env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .name("Kafka Source")
                .uid("kafka-source")
                .print()
                .name("Print to Console")
                .uid("print-to-console");


        env.execute("Kafka Source Connector with String Value Only");
    }
}
