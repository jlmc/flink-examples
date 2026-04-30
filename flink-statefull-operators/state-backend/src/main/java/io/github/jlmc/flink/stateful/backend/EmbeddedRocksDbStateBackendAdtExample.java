package io.github.jlmc.flink.stateful.backend;

import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * 4) State Backend (estratégia 2): EmbeddedRocksDBStateBackend.
 */
public class EmbeddedRocksDbStateBackendAdtExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        configureEmbeddedRocksDbStateBackend(env, "file:///tmp/flink/checkpoints/rocksdb");

        env.fromData(
                        "m-1,hospital-huc,ADT_A01,1000",
                        "m-2,hospital-huc,ADT_A03,2000"
                )
                .map(line -> "state-backend-demo: " + line)
                .print();

        env.execute("State Backend with ADT Events (RocksDB)");
    }

    public static void configureEmbeddedRocksDbStateBackend(StreamExecutionEnvironment env, String checkpointDir) {
        // Estratégia ativa para este exemplo: RocksDB embebido (estado grande com spill para disco).
        env.setStateBackend(new EmbeddedRocksDBStateBackend());

        // Código alternativo equivalente (recomendado em produção por configuração externa):
        // - flink-conf.yaml
        //   state.backend.type: rocksdb

        // Ativa checkpoints periódicos para suportar recuperação consistente.
        env.enableCheckpointing(10_000);

        // Código alternativo para otimizar cenários de backpressure/falhas:
        // env.getCheckpointConfig().enableUnalignedCheckpoints();
        // env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);

        // Storage de checkpoints (normalmente S3/HDFS/Azure Blob em produção).
        env.getCheckpointConfig().setCheckpointStorage(checkpointDir);

        // Código alternativo equivalente:
        // env.getCheckpointConfig().setCheckpointStorage(new FileSystemCheckpointStorage(checkpointDir));
    }
}
