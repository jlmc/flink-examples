package io.github.jlmc.j10;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.connector.kafka.util.JacksonMapperFactory;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CustomKeyValueKafkaRecordDeserializationSchema implements KafkaRecordDeserializationSchema<Tuple2<String, PersonLocationEvent>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomKeyValueKafkaRecordDeserializationSchema.class);

    private transient JsonDeserializationSchema<PersonLocationEvent> valueDeserializer;
    private transient ObjectMapper objectMapper;

    @Override
    public void open(DeserializationSchema.InitializationContext context) {
        LOGGER.info("Opening CustomKeyValueKafkaRecordDeserializationSchema");
        valueDeserializer = new JsonDeserializationSchema<>(PersonLocationEvent.class);
        objectMapper = JacksonMapperFactory.createObjectMapper();

        LOGGER.info("CustomKeyValueKafkaRecordDeserializationSchema opened successfully, object mapper {}", objectMapper.hashCode());
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> consumerRecord, Collector<Tuple2<String, PersonLocationEvent>> collector) throws IOException {
        LOGGER.debug("Deserializing record: topic={}, partition={}, offset={}", consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset());

        // when the key is an Interger we can use ByteBuffer to convert byte[] to integer
        // int key = ByteBuffer.wrap(consumerRecord.key()).getInt();
        // Deserialize the key (assuming UTF-8 encoding)

        String key = null;
        if (consumerRecord.key() != null) {
            key = new String(consumerRecord.key(), StandardCharsets.UTF_8);
        }

        // Deserialize the value (using JSON as an example)
        PersonLocationEvent value = null;
        if (consumerRecord.value() != null) {
            value = valueDeserializer.deserialize(consumerRecord.value());
        }

        // Emit the tuple
        LOGGER.trace("Emitting tuple: key={}, value={}", key, value);
        collector.collect(Tuple2.of(key, value));
    }

    @Override
    public TypeInformation<Tuple2<String, PersonLocationEvent>> getProducedType() {
        return new TupleTypeInfo<>(Types.TUPLE(Types.STRING, Types.POJO(PersonLocationEvent.class)));
    }
}
