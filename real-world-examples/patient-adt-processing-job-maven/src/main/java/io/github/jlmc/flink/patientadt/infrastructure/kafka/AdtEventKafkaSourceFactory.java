package io.github.jlmc.flink.patientadt.infrastructure.kafka;

import io.github.jlmc.flink.patientadt.app.model.AdtEvent;
import io.github.jlmc.flink.patientadt.components.serialization.AdtEventKeyedDeserializationSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceOptions;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;

public final class AdtEventKafkaSourceFactory {

    private AdtEventKafkaSourceFactory() {
    }

    public static KafkaSource<Tuple2<String, AdtEvent>> build(String bootstrapServers, String topic, String groupId) {
        return KafkaSource.<Tuple2<String, AdtEvent>>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(groupId)

                // forçar a leitura de todos os eventos desde o inicio do tópico.
                // .setStartingOffsets(OffsetsInitializer.earliest())
                .setStartingOffsets(OffsetsInitializer.timestamp(0L))
                .setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

                .setDeserializer(new AdtEventKeyedDeserializationSchema())
                .setProperty(KafkaSourceOptions.COMMIT_OFFSETS_ON_CHECKPOINT.key(), "true")
                //.setProperty(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")
                .build();
    }
}
