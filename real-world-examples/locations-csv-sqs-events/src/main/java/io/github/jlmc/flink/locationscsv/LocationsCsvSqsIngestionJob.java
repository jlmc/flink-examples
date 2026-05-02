package io.github.jlmc.flink.locationscsv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jlmc.flink.locationscsv.application.processing.FileProcessingResultAggregator;
import io.github.jlmc.flink.locationscsv.application.validation.LocationWithSourceBusinessValidator;
import io.github.jlmc.flink.locationscsv.domain.entity.FileProcessingMetric;
import io.github.jlmc.flink.locationscsv.domain.entity.FileProcessingResult;
import io.github.jlmc.flink.locationscsv.domain.entity.ValidationErrorWithSource;
import io.github.jlmc.flink.locationscsv.source.S3CsvObjectsFromSqsSource;
import io.github.jlmc.flink.locationscsv.source.S3ObjectCsvReaderFlatMap;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.nio.charset.StandardCharsets;

public class LocationsCsvSqsIngestionJob {

    private static final String AWS_ENDPOINT = env("AWS_ENDPOINT", "http://localhost:4566");
    private static final String AWS_REGION = env("AWS_REGION", "us-east-1");
    private static final String AWS_ACCESS_KEY = env("AWS_ACCESS_KEY_ID", "test");
    private static final String AWS_SECRET_KEY = env("AWS_SECRET_ACCESS_KEY", "test");
    private static final String SQS_QUEUE_URL = env("SQS_QUEUE_URL", "http://localhost:4566/000000000000/locations-csv-events");

    private static final String JDBC_URL = env("JDBC_URL", "jdbc:postgresql://localhost:5433/locations_db");
    private static final String JDBC_USER = env("JDBC_USER", "locations_user");
    private static final String JDBC_PASSWORD = env("JDBC_PASSWORD", "locations_password");

    private static final String KAFKA_BOOTSTRAP_SERVERS = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    private static final String KAFKA_TOPIC = env("KAFKA_TOPIC", "locations-file-processing-results");

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        DataStream<S3ObjectCsvReaderFlatMap.LocationWithSource> locationsFromSqsEvents = env
                .fromSource(
                        new S3CsvObjectsFromSqsSource(AWS_ENDPOINT, AWS_REGION, AWS_ACCESS_KEY, AWS_SECRET_KEY, SQS_QUEUE_URL),
                        WatermarkStrategy.noWatermarks(),
                        "sqs-s3-events")
                .name("sqs-s3-events")
                .flatMap(new S3ObjectCsvReaderFlatMap(AWS_ENDPOINT, AWS_REGION, AWS_ACCESS_KEY, AWS_SECRET_KEY))
                .name("read-csv-from-s3-object");

        SingleOutputStreamOperator<S3ObjectCsvReaderFlatMap.LocationWithSource> validatedLocations = locationsFromSqsEvents
                .process(new LocationWithSourceBusinessValidator())
                .name("location-business-validator")
                .setParallelism(10);

        DataStream<S3ObjectCsvReaderFlatMap.LocationWithSource> validRows = validatedLocations
                .filter(row -> !row.endOfFile())
                .name("valid-rows");

        DataStream<S3ObjectCsvReaderFlatMap.LocationWithSource> fileCompletedRows = validatedLocations
                .filter(S3ObjectCsvReaderFlatMap.LocationWithSource::endOfFile)
                .name("file-completed-markers");

        validRows.print("valid-locations");

        validRows.sinkTo(
                JdbcSink.<S3ObjectCsvReaderFlatMap.LocationWithSource>builder()
                        .withQueryStatement(
                                "INSERT INTO staging_locations (name, lat, lon, img_url, source_file_path) VALUES (?, ?, ?, ?, ?)",
                                (statement, row) -> {
                                    statement.setString(1, row.location().name());
                                    statement.setDouble(2, row.location().lat());
                                    statement.setDouble(3, row.location().lon());
                                    statement.setString(4, row.location().imgUrl());
                                    statement.setString(5, row.sourceFilePath());
                                }
                        )
                        .withExecutionOptions(
                                JdbcExecutionOptions.builder()
                                        .withBatchSize(200)
                                        .withBatchIntervalMs(1000)
                                        .withMaxRetries(3)
                                        .build()
                        )
                        .buildAtLeastOnce(
                                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                        .withUrl(JDBC_URL)
                                        .withDriverName("org.postgresql.Driver")
                                        .withUsername(JDBC_USER)
                                        .withPassword(JDBC_PASSWORD)
                                        .build()
                        )
        );

        DataStream<ValidationErrorWithSource> validationErrors = validatedLocations
                .getSideOutput(LocationWithSourceBusinessValidator.ERROR_TAG);

        validationErrors.print("validation-errors");

        DataStream<FileProcessingMetric> validMetrics = validRows
                .map(row -> new FileProcessingMetric(row.sourceFilePath(), FileProcessingMetric.MetricType.VALID_ROW, -1L, null))
                .name("valid-metrics");

        DataStream<FileProcessingMetric> invalidMetrics = validationErrors
                .map(error -> new FileProcessingMetric(error.sourceFilePath(), FileProcessingMetric.MetricType.INVALID_ROW, error.line(), error.error()))
                .name("invalid-metrics");

        DataStream<FileProcessingMetric> completedMetrics = fileCompletedRows
                .map(row -> new FileProcessingMetric(row.sourceFilePath(), FileProcessingMetric.MetricType.FILE_COMPLETED, -1L, null))
                .name("completed-metrics");

        DataStream<FileProcessingResult> fileResults = validMetrics
                .union(invalidMetrics, completedMetrics)
                .keyBy(FileProcessingMetric::sourceFilePath)
                .process(new FileProcessingResultAggregator())
                .name("file-processing-result-aggregator");

        fileResults.print("file-processing-results");

        KafkaSink<FileProcessingResult> resultKafkaSink = KafkaSink.<FileProcessingResult>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(KAFKA_TOPIC)
                                .setValueSerializationSchema(new FileProcessingResultJsonSerializer())
                                .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        fileResults.sinkTo(resultKafkaSink).name("file-results-kafka-sink");

        env.execute("SQS-S3-CSV-Ingestion-Job");

    }

    private static String env(String key, String defaultValue) {
        return System.getenv().getOrDefault(key, defaultValue);
    }

    private static class FileProcessingResultJsonSerializer implements SerializationSchema<FileProcessingResult> {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        @Override
        public byte[] serialize(FileProcessingResult element) {
            try {
                return OBJECT_MAPPER.writeValueAsString(element).getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize file processing result", e);
            }
        }
    }
}
