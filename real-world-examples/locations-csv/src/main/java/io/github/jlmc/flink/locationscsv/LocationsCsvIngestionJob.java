package io.github.jlmc.flink.locationscsv;

import io.github.jlmc.flink.locationscsv.application.validation.LocationBusinessValidator;
import io.github.jlmc.flink.locationscsv.domain.entity.Location;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.csv.CsvReaderFormat;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.jackson.JacksonMapperFactory;

import java.time.Duration;

public class LocationsCsvIngestionJob {

    public static void main(String[] args) throws Exception {
        final String inputPath = "/Users/jlmc/IdeaProjects/apache-flink/real-world-examples/locations-csv/input/";
        final String sourceFilePath = System.getenv().getOrDefault("SOURCE_FILE_PATH", inputPath);

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60000);

        // 1. Configure CSV reader for POJO
        CsvSchema csvSchema = JacksonMapperFactory.createCsvMapper()
                .schemaFor(Location.class)
                .withHeader()
                .withColumnSeparator(',');
        CsvReaderFormat<Location> locationCsvReaderFormat = CsvReaderFormat.forSchema(csvSchema, TypeInformation.of(Location.class));




        // 2. Source with continuous monitoring
        FileSource<Location> source = FileSource
                //.forRecordStreamFormat(locationCsvReaderFormat, new Path("s3://my-bucket/input/"))
                .forRecordStreamFormat(locationCsvReaderFormat, new Path(inputPath))
                .monitorContinuously(Duration.ofSeconds(10))
                .build();


        // 3. Processing pipeline
        SingleOutputStreamOperator<Location> validatedLocations = env
                .fromSource(source, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "S3-CSV-Source")
                .process(new LocationBusinessValidator())
                .setParallelism(10); // Increased to handle UrlValidator I/O

        validatedLocations.print("valid-locations");

        validatedLocations.sinkTo(
                JdbcSink.<Location>builder()
                        .withQueryStatement(
                                "INSERT INTO staging_locations (name, lat, lon, img_url, source_file_path) VALUES (?, ?, ?, ?, ?)",
                                (statement, location) -> {
                                    statement.setString(1, location.name());
                                    statement.setDouble(2, location.lat());
                                    statement.setDouble(3, location.lon());
                                    statement.setString(4, location.imgUrl());
                                    statement.setString(5, sourceFilePath);
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
                                        .withUrl("jdbc:postgresql://localhost:5432/locations_db")
                                        .withDriverName("org.postgresql.Driver")
                                        .withUsername("locations_user")
                                        .withPassword("locations_password")
                                        .build()
                        )
        );

        validatedLocations
                .getSideOutput(LocationBusinessValidator.ERROR_TAG)
                .print("validation-errors");

        env.execute("S3-POJO-Ingestion-Job");

    }
}
