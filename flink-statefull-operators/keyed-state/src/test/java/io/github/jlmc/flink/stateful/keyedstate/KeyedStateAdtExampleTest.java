package io.github.jlmc.flink.stateful.keyedstate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyedStateAdtExampleTest {

    @Test
    void shouldCreateSummaryAccumulator() {
        KeyedStateAdtExample.SummaryAccumulator accumulator = new KeyedStateAdtExample.SummaryAccumulator();
        accumulator.total = 3;
        accumulator.admissions = 2;

        assertThat(accumulator.total).isEqualTo(3);
        assertThat(accumulator.admissions).isEqualTo(2);
    }
}
