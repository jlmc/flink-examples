package io.github.jlmc.flink.stateful.backend;

import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * 4) State Backend (estratégia 1): HashMapStateBackend.
 */
public class HashMapStateBackendAdtExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        configureHashMapStateBackend(env, "file:///tmp/flink/checkpoints/hashmap");

        env.fromData(
                        "m-1,hospital-huc,ADT_A01,1000",
                        "m-2,hospital-huc,ADT_A03,2000"
                )
                .map(line -> "state-backend-demo: " + line)
                .print();

        env.execute("State Backend with ADT Events");
    }

    public static void configureHashMapStateBackend(StreamExecutionEnvironment env, String checkpointDir) {
        // Estratégia ativa para este exemplo: backend em memória (heap da JVM).
        env.setStateBackend(new HashMapStateBackend());

        // Código alternativo equivalente (recomendado em produção por configuração externa):
        // - flink-conf.yaml
        //   state.backend.type: hashmap

        // Ativa checkpoints periódicos para garantir recuperação de falhas.
        env.enableCheckpointing(10_000);

        // Código alternativo para tuning de checkpointing:
        // env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5_000);
        // env.getCheckpointConfig().setCheckpointTimeout(60_000);

        // Define explicitamente o storage dos checkpoints (durável e recuperável).
        env.getCheckpointConfig().setCheckpointStorage(checkpointDir);

        // Código alternativo equivalente:
        // env.getCheckpointConfig().setCheckpointStorage(new FileSystemCheckpointStorage(checkpointDir));
    }
}
