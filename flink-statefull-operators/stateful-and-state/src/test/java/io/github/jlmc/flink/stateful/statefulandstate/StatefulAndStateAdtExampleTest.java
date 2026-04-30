package io.github.jlmc.flink.stateful.statefulandstate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatefulAndStateAdtExampleTest {

    @Test
    void shouldCreateAdtEventPojo() {
        StatefulAndStateAdtExample.AdtEvent event =
                new StatefulAndStateAdtExample.AdtEvent("m-1", "hospital-huc", "ADT_A01", 1000L);

        assertThat(event.messageId).isEqualTo("m-1");
        assertThat(event.facilityId).isEqualTo("hospital-huc");
        assertThat(event.eventType).isEqualTo("ADT_A01");
        assertThat(event.eventTimestamp).isEqualTo(1000L);
    }
}
