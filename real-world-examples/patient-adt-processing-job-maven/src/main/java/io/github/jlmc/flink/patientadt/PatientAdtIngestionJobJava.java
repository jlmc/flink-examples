package io.github.jlmc.flink.patientadt;

import io.github.jlmc.flink.patientadt.app.model.AdtEvent;
import io.github.jlmc.flink.patientadt.app.model.AdtPatientLastLocation;
import io.github.jlmc.flink.patientadt.components.statefull.PatientLocationProcessFunction;
import io.github.jlmc.flink.patientadt.infrastructure.flink.StreamExecutionEnvironmentFactory;
import io.github.jlmc.flink.patientadt.infrastructure.kafka.AdtEventKafkaSourceFactory;
import io.github.jlmc.flink.patientadt.infrastructure.mongodb.AdtPatientLastLocationMongoSinkFactory;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
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
        long watermarkOutOfOrdernessMs = params.getLong("watermarkOutOfOrdernessMs", 5_000L);

        StreamExecutionEnvironment env = StreamExecutionEnvironmentFactory.build(params);

        Source<Tuple2<String, AdtEvent>, ?, ?> source = AdtEventKafkaSourceFactory.build(bootstrapServers, topic, groupId);

        Sink<AdtPatientLastLocation> mongoSink = AdtPatientLastLocationMongoSinkFactory
                .build(mongoUri, mongoDatabase, mongoCollection);

        definePipeline(
                env,
                source,
                mongoSink,
                parallelism,
                eventTtlDays,
                dischargedTtlDays,
                watermarkOutOfOrdernessMs
        );

        env.execute("PatientAdtIngestionJobJava");
    }

    public static void definePipeline(
            StreamExecutionEnvironment env,
            Source<Tuple2<String, AdtEvent>, ?, ?> source,
            Sink<AdtPatientLastLocation> sink,
            int parallelism,
            int eventTtlDays,
            int dischargedTtlDays,
            long watermarkOutOfOrdernessMs
    ) {
        WatermarkStrategy<Tuple2<String, AdtEvent>> watermarkStrategy = WatermarkStrategy
                .<Tuple2<String, AdtEvent>>forBoundedOutOfOrderness(Duration.ofMillis(watermarkOutOfOrdernessMs))
                .withTimestampAssigner((value, timestamp) -> {
                    AdtEvent event = value.f1;
                    if (event == null || event.getEventTimestamp() == null) {
                        return timestamp;
                    }
                    return event.getEventTimestamp().toEpochMilli();
                });

        DataStream<Tuple2<String, AdtEvent>> sourceStream = env.fromSource(source, watermarkStrategy, "Kafka Source")
                .setParallelism(parallelism)
                .name("Kafka Source");

        definePipeline(sourceStream, parallelism, eventTtlDays, dischargedTtlDays)
                .sinkTo(sink)
                .name("MongoDB Sink")
                .uid("mongodb-sink")
                .setParallelism(parallelism);
    }

    public static DataStream<AdtPatientLastLocation> definePipeline(
            DataStream<Tuple2<String, AdtEvent>> sourceStream,
            int parallelism,
            int eventTtlDays,
            int dischargedTtlDays
    ) {
        PatientLocationProcessFunction patientLocationProcessFunction = new PatientLocationProcessFunction(
                Duration.ofDays(eventTtlDays),
                Duration.ofDays(dischargedTtlDays)
        );

        return sourceStream
                .map(tuple -> tuple.f1)
                .name("Extract AdtEvent")
                .uid("extract-adt-event")
                .setParallelism(parallelism)

                .keyBy(AdtEvent::patientKey)

                .process(patientLocationProcessFunction)
                .name("Resolve Patient Location")
                .uid("patient-location-resolver")
                .setParallelism(parallelism)
                .name("Resolve Patient Location");
    }
}
