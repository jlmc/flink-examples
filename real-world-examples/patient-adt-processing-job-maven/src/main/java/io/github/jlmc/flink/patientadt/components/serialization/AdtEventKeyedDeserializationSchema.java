package io.github.jlmc.flink.patientadt.components.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jlmc.flink.patientadt.model.AdtEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.connector.kafka.util.JacksonMapperFactory;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AdtEventKeyedDeserializationSchema implements KafkaRecordDeserializationSchema<Tuple2<String, AdtEvent>>  {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdtEventKeyedDeserializationSchema.class);

    private transient JsonDeserializationSchema<AdtEvent> valueDeserializer;
    private transient ObjectMapper objectMapper;


    @Override
    public void open(DeserializationSchema.InitializationContext context) {
        LOGGER.info("Opening AdtEventKeyedDeserializationSchema");

        valueDeserializer = new JsonDeserializationSchema<>(AdtEvent.class);
        objectMapper = JacksonMapperFactory.createObjectMapper();
        valueDeserializer.open(context);

        LOGGER.info("AdtEventKeyedDeserializationSchema opened successfully, object mapper {}", objectMapper.hashCode());
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<Tuple2<String, AdtEvent>> collector) throws IOException {
        String key = record.key() != null ? new String(record.key(), StandardCharsets.UTF_8) : null;
        if (key == null) {
            LOGGER.warn("Received null key");
        }

        // Deserialize the value (using JSON as an example)
        AdtEvent value = record.value() != null
                ? valueDeserializer.deserialize(record.value())
                : null;

        if (value == null) {
            LOGGER.warn("Received null value for key {}", key);
        }

        // Emit the tuple
        LOGGER.trace("Emitting tuple: key={}, value={}", key, value);
        collector.collect(Tuple2.of(key, value));
    }

    @Override
    public TypeInformation<Tuple2<String, AdtEvent>> getProducedType() {
        return Types.TUPLE(Types.STRING, Types.POJO(AdtEvent.class));
    }
}
