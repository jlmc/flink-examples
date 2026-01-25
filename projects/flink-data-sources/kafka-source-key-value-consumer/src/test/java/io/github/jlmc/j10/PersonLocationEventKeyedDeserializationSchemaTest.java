package io.github.jlmc.j10;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PersonLocationEventKeyedDeserializationSchemaTest {

    private PersonLocationEventKeyedDeserializationSchema schema;

    @BeforeEach
    public void setUp() {
        schema = new PersonLocationEventKeyedDeserializationSchema();
        schema.open(null);
    }

    @Test
    public void shouldDeserializeValidKeyAndValue() throws IOException {
        String keyStr = "user-1";
        String valueJson = """
                {
                  "person_id": "user-1",
                  "latitude": 42.3118,
                  "longitude": -72.6882,
                  "event_timestamp": 1769358411300
                }
                """;
        byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = valueJson.getBytes(StandardCharsets.UTF_8);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 0, 0L, keyBytes, valueBytes);
        Collector<Tuple2<String, PersonLocationEvent>> collector = mock(Collector.class);

        schema.deserialize(record, collector);

        ArgumentCaptor<Tuple2<String, PersonLocationEvent>> captor = ArgumentCaptor.forClass(Tuple2.class);
        verify(collector).collect(captor.capture());

        Tuple2<String, PersonLocationEvent> result = captor.getValue();
        assertEquals(keyStr, result.f0);
        assertEquals("user-1", result.f1.personId());
        assertEquals(42.3118, result.f1.latitude());
        assertEquals(-72.6882, result.f1.longitude());
        assertEquals(1769358411300L, result.f1.eventTimestamp());
    }

    @Test
    public void shouldHandleNullKey() throws IOException {
        String valueJson = """
                {
                  "person_id": "user-1",
                  "latitude": 42.3118,
                  "longitude": -72.6882,
                  "event_timestamp": 1769358411300
                }
                """;
        byte[] valueBytes = valueJson.getBytes(StandardCharsets.UTF_8);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 0, 0L, null, valueBytes);
        Collector<Tuple2<String, PersonLocationEvent>> collector = mock(Collector.class);

        schema.deserialize(record, collector);

        ArgumentCaptor<Tuple2<String, PersonLocationEvent>> captor = ArgumentCaptor.forClass(Tuple2.class);
        verify(collector).collect(captor.capture());

        Tuple2<String, PersonLocationEvent> result = captor.getValue();
        assertNull(result.f0);
        assertEquals("user-1", result.f1.personId());
    }

    @Test
    public void shouldHandleNullValue() throws IOException {
        String keyStr = "user-1";
        byte[] keyBytes = keyStr.getBytes(StandardCharsets.UTF_8);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 0, 0L, keyBytes, null);
        Collector<Tuple2<String, PersonLocationEvent>> collector = mock(Collector.class);

        schema.deserialize(record, collector);

        ArgumentCaptor<Tuple2<String, PersonLocationEvent>> captor = ArgumentCaptor.forClass(Tuple2.class);
        verify(collector).collect(captor.capture());

        Tuple2<String, PersonLocationEvent> result = captor.getValue();
        assertEquals(keyStr, result.f0);
        assertNull(result.f1);
    }
}
