package io.github.jlmc.flink.sinks.mongodb;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.mongodb.sink.MongoSink;
import org.apache.flink.connector.mongodb.sink.writer.context.MongoSinkContext;
import org.apache.flink.connector.mongodb.sink.writer.serializer.MongoSerializationSchema;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;

import java.io.Serializable;
import java.util.Objects;

/**
 * Example of Flink MongoSink writing data to MongoDB.
 *
 * <p>To run this example, start the MongoDB service using the docker-compose.yaml in this module:
 * <pre>{@code
 * cd flink-sink-connectors/mongodb-sink-connector
 * docker-compose up -d
 * }</pre>
 */
public class MongoDBSinkConnectorExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Enable checkpointing for at-least-once or exactly-once semantics
        env.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);

        // Data Generator Source producing Patient objects
        // Using a limited range of IDs (0-9) to demonstrate UPSERT (Insert or Update)
        DataGeneratorSource<Patient> source = new DataGeneratorSource<>(
                value -> new Patient(value.intValue() % 10, "Patient-" + value, 20 + (int) (value % 50)),
                100L,
                RateLimiterStrategy.perSecond(1L),
                Types.POJO(Patient.class)
        );

        MongoSink<Patient> mongoSink = MongoSink.<Patient>builder()
                .setUri("mongodb://mongodb:27017")
                .setDatabase("flink_db")
                .setCollection("patients")
                .setSerializationSchema(new MongoSerializationSchema<Patient>() {
                    @Override
                    public WriteModel<BsonDocument> serialize(Patient patient, MongoSinkContext context) {
                        BsonDocument document = new BsonDocument();
                        document.append("id", new BsonInt32(patient.id));
                        document.append("name", new BsonString(patient.name));
                        document.append("age", new BsonInt32(patient.age));

                        // UPSERT logic: replace document if ID matches
                        BsonDocument filter = new BsonDocument("id", new BsonInt32(patient.id));
                        return new ReplaceOneModel<>(filter, document, new ReplaceOptions().upsert(true));
                    }
                })
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "mongodb-data-generator")
                .sinkTo(mongoSink);

        env.execute("Flink MongoDB Sink Connector Example");
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
            return "Patient{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", age=" + age +
                    '}';
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
