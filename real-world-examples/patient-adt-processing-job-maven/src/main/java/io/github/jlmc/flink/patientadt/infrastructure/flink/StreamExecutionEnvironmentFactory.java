package io.github.jlmc.flink.patientadt.infrastructure.flink;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.configuration.CheckpointingOptions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class StreamExecutionEnvironmentFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamExecutionEnvironmentFactory.class);

    private StreamExecutionEnvironmentFactory() {
    }

    public static StreamExecutionEnvironment build(ParameterTool params) {
        int parallelism = params.getInt("flinkParallelism", 1);
        long checkpointIntervalMs = params.getLong("checkpointIntervalMs", 30_000L);
        long minPauseBetweenCheckpointsMs = params.getLong("minPauseBetweenCheckpointsMs", 10_000L);
        long checkpointTimeoutMs = params.getLong("checkpointTimeoutMs", 2 * 60_000L);
        int tolerableCheckpointFailureNumber = params.getInt("tolerableCheckpointFailureNumber", 3);
        int maxConcurrentCheckpoints = params.getInt("maxConcurrentCheckpoints", 1);
        String checkpointsDirectory = params.get("checkpointsDirectory", "s3://flink-s3-bucket/patient-adt/checkpoints");
        String savepointsDirectory = params.get("savepointsDirectory", "s3://flink-s3-bucket/patient-adt/savepoints");
        boolean localDev = params.getBoolean("localDev", false);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(parallelism);

        Configuration runtimeConfig = new Configuration();
        runtimeConfig.set(CheckpointingOptions.CHECKPOINT_STORAGE, "filesystem");

        if (checkpointsDirectory != null && !checkpointsDirectory.isBlank()) {
            LOGGER.info("Configuring RocksDB state backend with checkpoint directory: {}", checkpointsDirectory);
            runtimeConfig.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
            runtimeConfig.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, checkpointsDirectory);
        }

        if (savepointsDirectory != null && !savepointsDirectory.isBlank()) {
            LOGGER.info("Configuring savepoint directory: {}", savepointsDirectory);
            runtimeConfig.set(CheckpointingOptions.SAVEPOINT_DIRECTORY, savepointsDirectory);
        }

        if (localDev) {
            setupLocalDevS3(params, runtimeConfig);
        }

        env.configure(runtimeConfig);
        registerCustomSerializers(env);

        env.enableCheckpointing(checkpointIntervalMs, CheckpointingMode.EXACTLY_ONCE);

        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(minPauseBetweenCheckpointsMs);
        env.getCheckpointConfig().setCheckpointTimeout(checkpointTimeoutMs);
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(tolerableCheckpointFailureNumber);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(maxConcurrentCheckpoints);
        env.getCheckpointConfig().enableExternalizedCheckpoints(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
        );
        env.getCheckpointConfig().enableUnalignedCheckpoints();

        return env;
    }

    private static void setupLocalDevS3(ParameterTool params, Configuration configuration) {
        String s3AccessKey = Objects.requireNonNull(
                params.get("s3AccessKey"),
                "s3AccessKey is mandatory when localDev is true"
        );
        String s3SecretKey = Objects.requireNonNull(
                params.get("s3SecretKey"),
                "s3SecretKey is mandatory when localDev is true"
        );
        String s3Endpoint = Objects.requireNonNull(
                params.get("s3Endpoint"),
                "s3Endpoint is mandatory when localDev is true"
        );

        LOGGER.info("Setting up local dev S3 with endpoint: {}", s3Endpoint);

        configuration.setString("s3.access-key", s3AccessKey);
        configuration.setString("s3.secret-key", s3SecretKey);
        configuration.setString("s3.endpoint", s3Endpoint);
        configuration.setString("s3.path.style.access", "true");

        FileSystem.initialize(configuration, null);
    }

    private static void registerCustomSerializers(StreamExecutionEnvironment env) {
        LOGGER.info("Registering custom Kryo serializers for Instant and LocalDate.");
        env.getConfig().registerTypeWithKryoSerializer(Instant.class, JavaSerializer.class);
        env.getConfig().registerTypeWithKryoSerializer(LocalDate.class, JavaSerializer.class);
    }

}
