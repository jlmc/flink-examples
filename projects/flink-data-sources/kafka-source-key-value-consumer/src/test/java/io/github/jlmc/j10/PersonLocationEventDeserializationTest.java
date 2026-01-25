package io.github.jlmc.j10;

import io.github.jlmc.j10.PersonLocationEvent;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PersonLocationEventDeserializationTest {

    @Test
    public void shouldDeserializePersonLocationEventWithRealisticTimestamp() throws Exception {
        String json = """
                {
                	"person_id": "user-1",
                	"latitude": 42.3118,
                	"longitude": -72.6882,
                	"event_timestamp": 1769358411300
                }
                """;

        JsonDeserializationSchema<PersonLocationEvent> schema = new JsonDeserializationSchema<>(PersonLocationEvent.class);
        schema.open(null);

        PersonLocationEvent event = schema.deserialize(json.getBytes());

        assertNotNull(event);
    }
}
