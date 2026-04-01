package io.github.jlmc.flink.sinks.csv;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.Encoder;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Example of a Flink Job that writes data to a Local File System in CSV format using FileSink and a custom Encoder.
 */
public class CsvSinkConnectorExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Enable checkpointing (required for FileSink to commit files)
        // Set it to 5 seconds to finalize files more quickly
        env.enableCheckpointing(5_000, CheckpointingMode.EXACTLY_ONCE);

        DataGeneratorSource<Patient> source = new DataGeneratorSource<>(
                value -> new Patient(value.intValue() % 10, "Patient " + (value % 10), 20 + (int) (value % 50)),
                Long.MAX_VALUE,
                org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy.perSecond(100),
                Types.POJO(Patient.class)
        );

        DataStream<Patient> dataStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "csv-generator");

        String outputPath = "/tmp/flink-output/csv-sink";

        // Use FileSink for writing CSV format.
        // We use a custom encoder to format the Patient POJO as a CSV line.
        FileSink<Patient> csvSink = FileSink
                .forRowFormat(new Path(outputPath), new PatientCsvEncoder())
                .withBucketAssigner(new org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner<>())
                .withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withRolloverInterval(Duration.ofSeconds(10))
                                .withInactivityInterval(Duration.ofSeconds(5))
                                .withMaxPartSize(MemorySize.parse("10KB"))
                                .build())
                .build();

        dataStream.sinkTo(csvSink);

        env.execute("Flink CSV Sink Connector Example");
    }

    public static class Patient implements Serializable {
        public Integer id;
        public String name;
        public Integer age;

        public Patient() {}

        public Patient(Integer id, String name, Integer age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return "Patient{id=" + id + ", name='" + name + "', age=" + age + "}";
        }
    }

    /**
     * A simple Encoder that converts a Patient object into a CSV line.
     */
    public static class PatientCsvEncoder implements Encoder<Patient> {
        @Override
        public void encode(Patient element, OutputStream stream) throws IOException {
            String line = String.format("%d,%s,%d\n", element.id, element.name, element.age);
            stream.write(line.getBytes(StandardCharsets.UTF_8));
        }
    }
}
