package io.github.jlmc.flink.sinks.kafka;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Example of Flink KafkaSink writing JSON messages to a Kafka topic using Flink's provided JsonSerializationSchema.
 */
public class KafkaSinkJsonExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Enable Flink checkpointing every 10 seconds (10,000 ms)
        // This is crucial for At-Least-Once or Exactly-Once delivery guarantees.
        env.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);

        DataGeneratorSource<Patient> source = new DataGeneratorSource<>(
                value -> new Patient(value.intValue(), "Patient-" + value, 20 + (int) (value % 50)),
                100L,
                RateLimiterStrategy.perSecond(1L),
                org.apache.flink.api.common.typeinfo.Types.POJO(Patient.class)
        );

        KafkaSink<Patient> sink = KafkaSink.<Patient>builder()
                .setBootstrapServers("kafka:19092")
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic("json-topic")
                                .setKeySerializationSchema((SerializationSchema<Patient>) patient ->
                                        String.valueOf(patient.id).getBytes(StandardCharsets.UTF_8))
                                .setValueSerializationSchema(new JsonSerializationSchema<>())
                                .build()
                )
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-json-generator")
                .sinkTo(sink);

        env.execute("Flink Kafka Sink JSON Example");
    }

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
            return "Patient{" + "id=" + id + ", name='" + name + '\'' + ", age=" + age + '}';
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
}
