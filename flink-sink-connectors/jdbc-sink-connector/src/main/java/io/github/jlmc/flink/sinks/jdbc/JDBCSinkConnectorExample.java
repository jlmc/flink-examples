package io.github.jlmc.flink.sinks.jdbc;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.jdbc.sink.JdbcSink;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Example of Flink JDBCSink writing data to PostgreSQL.
 *
 * <p>To run this example, start the PostgreSQL service using the docker-compose.yaml in this module:
 * <pre>{@code
 * cd flink-sink-connectors/jdbc-sink-connector
 * docker-compose up -d
 * }</pre>
 */
public class JDBCSinkConnectorExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Data Generator Source producing Patient objects
        // Using a limited range of IDs (0-9) to demonstrate UPSERT (Insert or Update)
        DataGeneratorSource<Patient> source = new DataGeneratorSource<>(
                value -> new Patient(value.intValue() % 10, "Patient-" + value, 20 + (int) (value % 50)),
                100L,
                RateLimiterStrategy.perSecond(1L),
                Types.POJO(Patient.class)
        );

        final String sql = "INSERT INTO patients (id, name, age) VALUES (?, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age";

        Sink<Patient> jdbcSink = JdbcSink.<Patient>builder()
                .withQueryStatement(sql, new JdbcStatementBuilder<Patient>() {
                    @Override
                    public void accept(PreparedStatement statement, Patient patient) throws SQLException {
                        statement.setInt(1, patient.id);
                        statement.setString(2, patient.name);
                        statement.setInt(3, patient.age);
                    }
                })
                .withExecutionOptions(JdbcExecutionOptions.builder()
                        .withBatchSize(100)
                        .withBatchIntervalMs(200)
                        .withMaxRetries(5)
                        .build())
                .buildAtLeastOnce(new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl("jdbc:postgresql://postgres:5432/flink_db")
                        .withDriverName("org.postgresql.Driver")
                        .withUsername("flink_user")
                        .withPassword("flink_password")
                        .build());

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "jdbc-data-generator")
                .sinkTo(jdbcSink);

        env.execute("Flink JDBC Sink Connector Example (PostgreSQL)");
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
