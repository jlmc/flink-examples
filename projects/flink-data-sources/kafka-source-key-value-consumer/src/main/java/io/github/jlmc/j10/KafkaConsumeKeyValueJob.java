package io.github.jlmc.j10;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
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
public class KafkaConsumeKeyValueJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumeKeyValueJob.class);

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        // Set the web UI port to 8081 (or another free port) to avoid conflicts if multiple jobs are running
        conf.set(RestOptions.PORT, 8081);
        conf.set(TaskManagerOptions.NUM_TASK_SLOTS, 4);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);

        DataStream<Tuple2<String, PersonLocationEvent>> stream = createJob(env, "localhost:9092");

        stream.map((Tuple2<String, PersonLocationEvent> it) -> "Received event key: " + it.f0 + ", value: " + it.f1)
                .returns(Types.STRING)
                .print()
                .name("Print to Console")
                .uid("print-to-console");

        env.execute("Kafka Source Connector with String Value Only");
    }

    public static DataStream<Tuple2<String, PersonLocationEvent>> createJob(StreamExecutionEnvironment env, String bootstrapServers) {
        LOGGER.info("Hello, Kafka Source Connector with String Value Only!");

        env.setParallelism(1); // Set parallelism to 1 for simplicity, by default it is the number of CPU cores

        env.enableCheckpointing(3000, CheckpointingMode.EXACTLY_ONCE);

        JsonDeserializationSchema<PersonLocationEvent> deserializationSchema = new JsonDeserializationSchema<>(PersonLocationEvent.class);

        KafkaSource<Tuple2<String, PersonLocationEvent>> kafkaSource =
                KafkaSource.<Tuple2<String, PersonLocationEvent>>builder()
                        .setBootstrapServers(bootstrapServers)
                        .setTopics("person-location-events")
                        .setGroupId("KafkaSourceJsonConsumerJob")
                        // configure other options as needed
                        .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                        //.setBounded(org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer.latest())

                        //.setValueOnlyDeserializer(deserializationSchema)
                        .setDeserializer(new PersonLocationEventKeyedDeserializationSchema())

                        .setProperty(KafkaSourceOptions.COMMIT_OFFSETS_ON_CHECKPOINT.key(), "true")
                        //.setProperty(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
                        .build();

        return env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .name("Kafka Source")
                .uid("kafka-source")
                ;
    }
}
