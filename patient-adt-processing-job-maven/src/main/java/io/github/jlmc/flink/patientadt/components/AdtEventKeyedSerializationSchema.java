package io.github.jlmc.flink.patientadt.components;

import io.github.jlmc.flink.patientadt.model.AdtEvent;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.checkerframework.checker.nullness.qual.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AdtEventKeyedSerializationSchema implements KafkaRecordSerializationSchema<Tuple2<String, AdtEvent>> {

    private transient JacksonSerializationSchema<AdtEvent> serializationSchema;


    @Override
    public void open(SerializationSchema.InitializationContext context, KafkaSinkContext sinkContext) throws Exception {
        KafkaRecordSerializationSchema.super.open(context, sinkContext);

        this.serializationSchema = new JacksonSerializationSchema<>();

    }

    @Override
    public @Nullable ProducerRecord<byte[], byte[]> serialize(Tuple2<String, AdtEvent> element,
                                                              KafkaSinkContext context,
                                                              Long timestamp) {

        if (element == null) {
            return null;
        }

        // key (String → bytes)
        byte[] key = element.f0 != null
                ? element.f0.getBytes(UTF_8)
                : null;

        // value (AdtEvent → JSON bytes)
        byte[] value = serializationSchema.serialize(element.f1);


        return null;
    }
}
