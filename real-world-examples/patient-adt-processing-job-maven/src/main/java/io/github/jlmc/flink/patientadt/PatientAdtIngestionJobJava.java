package io.github.jlmc.flink.patientadt;

import io.github.jlmc.flink.patientadt.app.model.AdtEvent;
import io.github.jlmc.flink.patientadt.app.model.AdtPatientLastLocation;
import io.github.jlmc.flink.patientadt.components.statefull.PatientLocationProcessFunction;
import io.github.jlmc.flink.patientadt.infrastructure.flink.StreamExecutionEnvironmentFactory;
import io.github.jlmc.flink.patientadt.infrastructure.kafka.AdtEventKafkaSourceFactory;
import io.github.jlmc.flink.patientadt.infrastructure.mongodb.AdtPatientLastLocationMongoSinkFactory;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
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

        KafkaSource<Tuple2<String, AdtEvent>> source = AdtEventKafkaSourceFactory.build(bootstrapServers, topic, groupId);

        Sink<AdtPatientLastLocation> mongoSink = AdtPatientLastLocationMongoSinkFactory
                .build(mongoUri, mongoDatabase, mongoCollection);

        definePipeline(
                env,
                source,
                mongoSink,
                eventTtlDays,
                dischargedTtlDays,
                watermarkOutOfOrdernessMs
        );

        env.execute("PatientAdtIngestionJobJava");
    }

    public static void definePipeline(
            StreamExecutionEnvironment env,
            KafkaSource<Tuple2<String, AdtEvent>> source,
            Sink<AdtPatientLastLocation> sink,
            int eventTtlDays,
            int dischargedTtlDays,
            long watermarkOutOfOrdernessMs
    ) {


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

        env.fromSource(source, watermarkStrategy, "Kafka Source")
                //.assignTimestampsAndWatermarks(watermarkStrategy)
                .map(it -> it.f1)
                .name("Extract AdtEvent")
                .uid("extract-adt-event")

                .keyBy(AdtEvent::patientKey)

                .process(patientLocationProcessFunction)
                .name("Resolve Patient Location")
                .uid("patient-location-resolver")

                .sinkTo(sink)
                .name("MongoDB Sink")
                .uid("mongodb-sink");
    }
}
