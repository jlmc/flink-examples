package io.github.jlmc.flink.stateful.backend;

import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * 4) State Backend: HashMapStateBackend e EmbeddedRocksDBStateBackend.
 */
public class StateBackendAdtExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        configureHashMapStateBackend(env, "file:///tmp/flink/checkpoints/hashmap");
        // Alternativa:
        // configureEmbeddedRocksDbStateBackend(env, "file:///tmp/flink/checkpoints/rocksdb");

        env.fromData(
                        "m-1,hospital-huc,ADT_A01,1000",
                        "m-2,hospital-huc,ADT_A03,2000"
                )
                .map(line -> "state-backend-demo: " + line)
                .print();

        env.execute("State Backend with ADT Events");
    }

    public static void configureHashMapStateBackend(StreamExecutionEnvironment env, String checkpointDir) {
        env.setStateBackend(new HashMapStateBackend());
        env.enableCheckpointing(10_000);
        env.getCheckpointConfig().setCheckpointStorage(checkpointDir);
    }

    public static void configureEmbeddedRocksDbStateBackend(StreamExecutionEnvironment env, String checkpointDir) {
        env.setStateBackend(new EmbeddedRocksDBStateBackend());
        env.enableCheckpointing(10_000);
        env.getCheckpointConfig().setCheckpointStorage(checkpointDir);
    }
}
