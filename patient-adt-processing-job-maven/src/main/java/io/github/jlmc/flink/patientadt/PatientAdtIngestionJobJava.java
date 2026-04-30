package io.github.jlmc.flink.patientadt;

import io.github.jlmc.flink.patientadt.components.AdtEventKeyedDeserializationSchema;
import io.github.jlmc.flink.patientadt.model.AdtEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceOptions;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;

public class PatientAdtIngestionJobJava {

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);

        String bootstrapServers = params.get("kafkaBootstrapServers", "kafka:19092");
        String topic = params.get("kafkaTopic", "hls-providers.hl7.adt");
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
                // .setValueOnlyDeserializer(new SimpleStringSchema())
                //.setValueOnlyDeserializer(deserializationSchema)
                //.setDeserializer(new PersonLocationEventKeyedDeserializationSchema())
                .setDeserializer(new AdtEventKeyedDeserializationSchema())
                .setProperty(KafkaSourceOptions.COMMIT_OFFSETS_ON_CHECKPOINT.key(), "true")
                //.setProperty(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
                .build();





        KafkaSink<Tuple2<String, String>> sink = KafkaSink.<Tuple2<String, String>>builder()
                .setBootstrapServers("kafka:19092")
                .setRecordSerializer((tuple, context, timestamp) -> new ProducerRecord<>(
                        "key-value-topic",
                        tuple.f0.getBytes(StandardCharsets.UTF_8),
                        tuple.f1.getBytes(StandardCharsets.UTF_8)
                ))
                .build();


        env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-key-value-generator")
                .map(value -> {
                    // Here you would parse the HL7 ADT message and extract relevant information
                    // For simplicity, we're just returning the raw message as the value
                    return value;
                })

                .map(value -> Tuple2.of("key", value)) // Simple mapping to create a key-value tuple
                .sinkTo(sink);


        env.execute("PatientAdtIngestionJobJava");
    }
}
