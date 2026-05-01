package io.github.jlmc.flink.patientadt;

import io.github.jlmc.flink.patientadt.components.AdtEventKeyedDeserializationSchema;
import io.github.jlmc.flink.patientadt.components.JacksonSerializationSchema;
import io.github.jlmc.flink.patientadt.components.statefull.PatientLocationProcessFunction;
import io.github.jlmc.flink.patientadt.model.AdtEvent;
import io.github.jlmc.flink.patientadt.model.AdtPatientLastLocation;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceOptions;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.nio.charset.StandardCharsets;
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

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);

        KafkaSource<Tuple2<String, AdtEvent>> source = KafkaSource.<Tuple2<String, AdtEvent>>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(new AdtEventKeyedDeserializationSchema())
                .setProperty(KafkaSourceOptions.COMMIT_OFFSETS_ON_CHECKPOINT.key(), "true")
                //.setProperty(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
                .build();


        KafkaSink<AdtPatientLastLocation> sink = KafkaSink.<AdtPatientLastLocation>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<AdtPatientLastLocation>builder()
                                .setTopic("result-data")
                                .setKeySerializationSchema((SerializationSchema<AdtPatientLastLocation>) event ->
                                        event == null || event.patientId() == null || event.accountId() == null
                                                ? null
                                                : (event.accountId() + "_" + event.patientId()).getBytes(StandardCharsets.UTF_8)
                                )
                                .setValueSerializationSchema(new JacksonSerializationSchema<>())
                                .build()
                )
                .build();


        env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-key-value-generator")

                .map(it -> it.f1)
                .name("Extract AdtEvent")
                .uid("extract-adt-event")

                .keyBy(AdtEvent::patientKey)

                .process(new PatientLocationProcessFunction(
                        Duration.ofDays(eventTtlDays),
                        Duration.ofDays(dischargedTtlDays)
                ))


                .sinkTo(sink);


        env.execute("PatientAdtIngestionJobJava");
    }
}
