package io.github.jlmc.flink.patientadt.components.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.connector.kafka.util.JacksonMapperFactory;

import java.nio.charset.StandardCharsets;

public class JacksonSerializationSchema<T> implements SerializationSchema<T> {
    private transient ObjectMapper mapper;

    public JacksonSerializationSchema() {
    }

    @Override
    public void open(InitializationContext context) {
        this.mapper = JacksonMapperFactory.createObjectMapper();
    }

    @Override
    public byte[] serialize(T element) {
        try {
            return mapper.writeValueAsString(element).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
