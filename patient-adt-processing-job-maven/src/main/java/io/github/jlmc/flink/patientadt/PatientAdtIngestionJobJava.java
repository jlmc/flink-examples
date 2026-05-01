package io.github.jlmc.flink.patientadt;

import io.github.jlmc.flink.patientadt.components.serialization.AdtEventKeyedDeserializationSchema;
import io.github.jlmc.flink.patientadt.components.statefull.PatientLocationProcessFunction;
import io.github.jlmc.flink.patientadt.infrastructure.mongodb.AdtPatientLastLocationMongoSerializationSchema;
import io.github.jlmc.flink.patientadt.model.AdtEvent;
import io.github.jlmc.flink.patientadt.model.AdtPatientLastLocation;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.mongodb.sink.MongoSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceOptions;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;

public class PatientAdtIngestionJobJava {

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);

        String bootstrapServers = params.get("kafkaBootstrapServers", "kafka:19092");
        String topic = params.get("kafkaTopic", "adt-events-data");
        String groupId = params.get("kafkaGroupId", "patient-adt-processing-job-java");
        int parallelism = params.getInt("flinkParallelism", 1);
        int eventTtlDays = params.getInt("eventTtlInDays", 5);
        int dischargedTtlDays = params.getInt("dischargedTtlInDays", 2);
        String mongoUri = params.get("mongoUri", "mongodb://admin:admin123@mongodb:27017");
        String mongoDatabase = params.get("mongoDatabase", "patient_adt");
        String mongoCollection = params.get("mongoCollection", "adt_patient_last_location");
        long checkpointIntervalMs = params.getLong("checkpointIntervalMs", 30_000L);
        long minPauseBetweenCheckpointsMs = params.getLong("minPauseBetweenCheckpointsMs", 10_000L);
        long checkpointTimeoutMs = params.getLong("checkpointTimeoutMs", 2 * 60_000L);
        int tolerableCheckpointFailureNumber = params.getInt("tolerableCheckpointFailureNumber", 3);
        int maxConcurrentCheckpoints = params.getInt("maxConcurrentCheckpoints", 1);
        long watermarkOutOfOrdernessMs = params.getLong("watermarkOutOfOrdernessMs", 5_000L);
        String checkpointsDirectory = params.get("checkpointsDirectory", "s3://flink-s3-bucket/patient-adt/checkpoints");
        String savepointsDirectory = params.get("savepointsDirectory", "s3://flink-s3-bucket/patient-adt/savepoints");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);
        Configuration runtimeConfig = new Configuration();
        runtimeConfig.set(CheckpointingOptions.SAVEPOINT_DIRECTORY, savepointsDirectory);
        env.configure(runtimeConfig);
        env.setStateBackend(new HashMapStateBackend());
        env.enableCheckpointing(checkpointIntervalMs, CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setCheckpointStorage(checkpointsDirectory);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(minPauseBetweenCheckpointsMs);
        env.getCheckpointConfig().setCheckpointTimeout(checkpointTimeoutMs);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(tolerableCheckpointFailureNumber);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(maxConcurrentCheckpoints);
        env.getCheckpointConfig().enableExternalizedCheckpoints(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
        );
        env.getCheckpointConfig().enableUnalignedCheckpoints();

        KafkaSource<Tuple2<String, AdtEvent>> source = KafkaSource.<Tuple2<String, AdtEvent>>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(new AdtEventKeyedDeserializationSchema())
                .setProperty(KafkaSourceOptions.COMMIT_OFFSETS_ON_CHECKPOINT.key(), "true")
                //.setProperty(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
                .build();


        MongoSink<AdtPatientLastLocation> mongoSink = MongoSink.<AdtPatientLastLocation>builder()
                .setUri(mongoUri)
                .setDatabase(mongoDatabase)
                .setCollection(mongoCollection)
                .setSerializationSchema(new AdtPatientLastLocationMongoSerializationSchema())
                .build();


        PatientLocationProcessFunction patientLocationProcessFunction = new PatientLocationProcessFunction(
                Duration.ofDays(eventTtlDays),
                Duration.ofDays(dischargedTtlDays)
        );

        WatermarkStrategy<Tuple2<String, AdtEvent>> watermarkStrategy = WatermarkStrategy
                .<Tuple2<String, AdtEvent>>forBoundedOutOfOrderness(Duration.ofMillis(watermarkOutOfOrdernessMs))
                .withTimestampAssigner((value, timestamp) -> {
                    AdtEvent event = value.f1;
                    if (event == null || event.getEventTimestamp() == null) {
                        return timestamp;
                    }
                    return event.getEventTimestamp().toEpochMilli();
                });

        env.fromSource(source, watermarkStrategy, "kafka-key-value-generator")
                .map(it -> it.f1)
                .name("Extract AdtEvent")
                .uid("extract-adt-event")

                .keyBy(AdtEvent::patientKey)

                .process(patientLocationProcessFunction)
                .name("Resolve Patient Location")
                .uid("patient-location-resolver")

                .sinkTo(mongoSink)
                .name("MongoDB Sink")
                .uid("mongodb-sink");


        env.execute("PatientAdtIngestionJobJava");
    }
}
