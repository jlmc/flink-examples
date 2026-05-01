package io.github.jlmc.flink.patientadt;

import io.github.jlmc.flink.patientadt.components.AdtEventKeyedDeserializationSchema;
import io.github.jlmc.flink.patientadt.model.AdtEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceOptions;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.nio.charset.StandardCharsets;

//         /usr/bin/kafka-topics --create --if-not-exists --topic adt-events-data --bootstrap-server kafka:19092 --partitions 1 --replication-factor 1
//        /usr/bin/kafka-topics --create --if-not-exists --topic result-data --bootstrap-server kafka:19092 --partitions 1 --replication-factor 1
//
public class PatientAdtIngestionJobJava {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                // .setValueOnlyDeserializer(new SimpleStringSchema())
                //.setValueOnlyDeserializer(deserializationSchema)
                //.setDeserializer(new PersonLocationEventKeyedDeserializationSchema())
                .setDeserializer(new AdtEventKeyedDeserializationSchema())
                .setProperty(KafkaSourceOptions.COMMIT_OFFSETS_ON_CHECKPOINT.key(), "true")
                //.setProperty(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
                .build();





        KafkaSink<Tuple2<String, AdtEvent>> sink = KafkaSink.<Tuple2<String, AdtEvent>>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<Tuple2<String, AdtEvent>>builder()
                                .setTopic("result-data")
                                .setKeySerializationSchema((SerializationSchema<Tuple2<String, AdtEvent>>) tuple ->
                                        tuple.f0 == null ? null : tuple.f0.getBytes(StandardCharsets.UTF_8)
                                )
                                .setValueSerializationSchema((SerializationSchema<Tuple2<String, AdtEvent>>) tuple ->
                                        toJson(tuple.f1).getBytes(StandardCharsets.UTF_8)
                                )
                                .build()
                )
                .build();


        env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-key-value-generator")
                .sinkTo(sink);


        env.execute("PatientAdtIngestionJobJava");
    }

    private static String toJson(AdtEvent event) {
        try {
            ObjectNode json = OBJECT_MAPPER.createObjectNode();
            json.put("accountId", event.accountId);
            json.put("patientId", event.patientId);
            json.put("eventType", event.eventType);
            json.put("locationId", event.locationId);
            json.put("eventTimestamp", event.eventTimestamp == null ? null : event.eventTimestamp.toString());

            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AdtEvent", e);
        }
    }
}
