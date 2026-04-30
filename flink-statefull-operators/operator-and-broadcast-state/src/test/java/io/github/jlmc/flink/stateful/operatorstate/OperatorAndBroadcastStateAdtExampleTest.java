package io.github.jlmc.flink.stateful.operatorstate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorAndBroadcastStateAdtExampleTest {

    @Test
    void shouldBuildAdtEvent() {
        OperatorAndBroadcastStateAdtExample.AdtEvent event =
                new OperatorAndBroadcastStateAdtExample.AdtEvent("m-1", "hospital-huc", "ADT_A01", 1000L);

        assertThat(event.facilityId).isEqualTo("hospital-huc");
        assertThat(event.eventType).isEqualTo("ADT_A01");
    }
}
