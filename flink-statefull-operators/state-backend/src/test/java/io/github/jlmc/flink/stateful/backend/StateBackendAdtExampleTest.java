package io.github.jlmc.flink.stateful.backend;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class StateBackendAdtExampleTest {

    @Test
    void shouldConfigureHashMapStateBackend() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        assertThatCode(() -> HashMapStateBackendAdtExample.configureHashMapStateBackend(env, "file:///tmp/flink/checkpoints/hashmap-test"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldConfigureEmbeddedRocksDbStateBackend() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        assertThatCode(() -> EmbeddedRocksDbStateBackendAdtExample.configureEmbeddedRocksDbStateBackend(env, "file:///tmp/flink/checkpoints/rocksdb-test"))
                .doesNotThrowAnyException();
    }
}
