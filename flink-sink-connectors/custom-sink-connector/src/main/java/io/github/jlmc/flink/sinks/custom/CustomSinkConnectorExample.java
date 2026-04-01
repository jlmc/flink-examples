package io.github.jlmc.flink.sinks.custom;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Example of a Flink Job that uses a Custom Sink implementation (SinkV2).
 * This example implements a more "real-world" scenario: an HTTP Sink that
 * sends data to a REST API (mocked by MockServer).
 */
public class CustomSinkConnectorExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(5_000, CheckpointingMode.EXACTLY_ONCE);

        DataGeneratorSource<Patient> source = new DataGeneratorSource<>(
                value -> new Patient(value.intValue(), "Patient " + value),
                Long.MAX_VALUE,
                org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy.perSecond(2),
                Types.POJO(Patient.class)
        );

        env.fromSource(source, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "patient-generator")
                .sinkTo(new HttpSink("http://mockserver:1080/api/patients"));

        env.execute("Flink Custom HTTP Sink Example");
    }

    public static class Patient implements Serializable {
        public int id;
        public String name;

        public Patient() {}
        public Patient(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "Patient{id=" + id + ", name='" + name + "'}";
        }
    }

    /**
     * A custom Sink that sends data to an HTTP endpoint.
     */
    public static class HttpSink implements Sink<Patient>, Serializable {
        private final String endpoint;

        public HttpSink(String endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public SinkWriter<Patient> createWriter(InitContext context) {
            return new HttpSinkWriter(endpoint);
        }
    }

    public static class HttpSinkWriter implements SinkWriter<Patient> {
        private static final Logger LOG = LoggerFactory.getLogger(HttpSinkWriter.class);
        private final String endpoint;
        private final HttpClient httpClient;
        private final SerializationSchema<Patient> serializationSchema;

        public HttpSinkWriter(String endpoint) {
            this.endpoint = endpoint;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            this.serializationSchema = new JsonSerializationSchema<>(ObjectMapper::new);
            try {
                this.serializationSchema.open(null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to open serialization schema", e);
            }
        }

        @Override
        public void write(Patient element, Context context) throws IOException, InterruptedException {
            byte[] bytes = serializationSchema.serialize(element);
            String json = new String(bytes, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            LOG.info("Sending patient to HTTP Sink: {}", element);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.debug("Successfully sent patient to HTTP Sink. Status: {}", response.statusCode());
            } else {
                LOG.error("Failed to send patient to HTTP Sink. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new IOException("Failed to send data to HTTP Sink. Status code: " + response.statusCode());
            }
        }

        @Override
        public void flush(boolean endOfInput) {
            // Nothing to flush as we send records synchronously in this simple example
        }

        @Override
        public void close() {
            // HttpClient doesn't necessarily need closing in this version of Java,
            // but you might want to shutdown executor services if you used a custom one.
        }
    }
}
