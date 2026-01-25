package io.github.jlmc.j9;

import io.github.jlmc.j9.model.PersonLocationEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceOptions;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * kafka-source-json-consumer
 */
public class KafkaSourceJsonConsumerJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSourceJsonConsumerJob.class);

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        // Set the web UI port to 8081 (or another free port) to avoid conflicts if multiple jobs are running
        conf.set(RestOptions.PORT, 8081);
        conf.set(TaskManagerOptions.NUM_TASK_SLOTS, 4);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);

        DataStream<PersonLocationEvent> stream = createJob(env, "localhost:9092");

        stream.print()
              .name("Print to Console")
              .uid("print-to-console");

        env.execute("Kafka Source Connector with String Value Only");
    }

    public static DataStream<PersonLocationEvent> createJob(StreamExecutionEnvironment env, String bootstrapServers) {
        LOGGER.info("Hello, Kafka Source Connector with String Value Only!");

        env.setParallelism(1); // Set parallelism to 1 for simplicity, by default it is the number of CPU cores

        env.enableCheckpointing(3000, CheckpointingMode.EXACTLY_ONCE);

        JsonDeserializationSchema<PersonLocationEvent> deserializationSchema = new JsonDeserializationSchema<>(PersonLocationEvent.class);

        KafkaSource<PersonLocationEvent> kafkaSource =
                KafkaSource.<PersonLocationEvent>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics("person-location-events")
                        .setGroupId("KafkaSourceJsonConsumerJob")
                        // configure other options as needed
                        .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                        //.setBounded(org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer.latest())

                        .setValueOnlyDeserializer(deserializationSchema)

                        .setProperty(KafkaSourceOptions.COMMIT_OFFSETS_ON_CHECKPOINT.key(), "true")
                        //.setProperty(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
                        .build();

        return env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .name("Kafka Source")
                .uid("kafka-source");
    }
}
