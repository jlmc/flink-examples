package io.github.jlmc.flink.sinks;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.github.jlmc.flink.sinks.common.JacksonCsvEncoder;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;

import java.io.Serializable;
import java.util.Objects;

/**
 * Example of Flink FileSink writing CSV data to AWS S3 (simulated by MinIO).
 *
 * <p>To run this example, start the MinIO service using the docker-compose.yaml in this module:
 * <pre>{@code
 * cd flink-sink-connectors/s3-sink-connector
 * docker-compose up -d
 * }</pre>
 */
public class S3SinkConnectorExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);

        // Data Generator Source producing Patient objects
        DataGeneratorSource<Patient> source = new DataGeneratorSource<>(
                value -> new Patient(value.intValue(), "Patient-" + value, 20 + (int)(value % 50)),
                1000L,
                RateLimiterStrategy.perSecond(1L),
                Types.POJO(Patient.class)
        );

        String s3Path = "s3a://flink-s3-bucket/output";

        FileSink<Patient> s3Sink = FileSink
                .forRowFormat(new Path(s3Path), new JacksonCsvEncoder<>(Patient.class))
                .withBucketAssigner(new DateTimeBucketAssigner<>("yyyy-MM-dd-HH"))
                .withRollingPolicy(
                        org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy.builder()
                                .withRolloverInterval(java.time.Duration.ofMinutes(1))
                                .withInactivityInterval(java.time.Duration.ofSeconds(10))
                                .withMaxPartSize(1024 * 1024)
                                .build())
                .withOutputFileConfig(OutputFileConfig.builder()
                        .withPartPrefix("s3-patients")
                        .withPartSuffix(".txt")
                        .build())
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "s3-data-generator")
                .sinkTo(s3Sink);

        env.execute("Flink S3 Sink Connector Example (CSV/MinIO)");
    }


    @JsonPropertyOrder({"id", "name", "age"})
    public static class Patient implements Serializable {
        public int id;
        public String name;
        public int age;

        public Patient() {}

        public Patient(int id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return id + "," + name + "," + age;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Patient patient = (Patient) o;
            return id == patient.id && age == patient.age && Objects.equals(name, patient.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name, age);
        }
    }

    private static Configuration getConfiguration(String[] args) {
        ParameterTool params = ParameterTool.fromArgs(args);

        String s3Endpoint = params.get("s3.endpoint", "http://localhost:9000");
        String s3AccessKey = params.get("s3.access-key", "minio");
        String s3SecretKey = params.get("s3.secret-key", "minio123");

        // Configuration for S3/MinIO
        Configuration config = new Configuration();

        configureAwsS3(config, s3Endpoint, s3AccessKey, s3SecretKey);
        return config;
    }

    private static void configureAwsS3(Configuration config, String s3Endpoint, String s3AccessKey, String s3SecretKey) {
        // Hadoop S3A keys (used by flink-s3-fs-hadoop)
        config.setString("fs.s3a.endpoint", s3Endpoint);
        config.setString("fs.s3a.access.key", s3AccessKey);
        config.setString("fs.s3a.secret.key", s3SecretKey);
        config.setBoolean("fs.s3a.path.style.access", true); // Required for MinIO
        config.setString("fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider");

        // Ensure that S3A connector doesn't try to get region from AWS (important for MinIO)
        config.setBoolean("fs.s3a.endpoint.region.sigv4.override", true);
        config.setString("fs.s3a.signing-algorithm", "S3SignerType");
    }
}
