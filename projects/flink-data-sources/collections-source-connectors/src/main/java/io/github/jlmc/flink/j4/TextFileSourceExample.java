package io.github.jlmc.flink.j4;

import io.github.jlmc.flink.j4.models.Person;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.csv.CsvReaderFormat;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;

/// In order to use the File Source connector, you need to include the following Maven dependency:
/// ```
/// <dependency>
///     <groupId>org.apache.flink</groupId>
///     <artifactId>flink-connector-files</artifactId>
///     <version>${flink.version}</version>
/// </dependency>
///```
///
public class TextFileSourceExample {

    public static SingleOutputStreamOperator<String> buildStream(StreamExecutionEnvironment env, String filePath) {

        FileSource<String> fileSource =
                FileSource.forRecordStreamFormat(
                        new TextLineInputFormat(),
                        new org.apache.flink.core.fs.Path(filePath)
                ).build();

        env.setParallelism(1);
        return env.fromSource(fileSource, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "file-source");
    }

    public static SingleOutputStreamOperator<String> buildBoundedStream(StreamExecutionEnvironment env, String filePath) {
        FileSource<String> fileSource =
                FileSource.forRecordStreamFormat(
                        new TextLineInputFormat(),
                        new org.apache.flink.core.fs.Path(filePath)
                )
                        .monitorContinuously(Duration.ofSeconds(10)) // Enable continuous monitoring for new files
                        .build();

        env.setParallelism(1);
        return env.fromSource(fileSource, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "file-source");
    }

    /// Example of reading CSV files into POJOs, is necessary to include the dependency:
    /// ```
    /// <dependency>
    ///     <groupId>org.apache.flink</groupId>
    ///     <artifactId>flink-csv</artifactId>
    ///     <version>${flink.version}</version>
    /// </dependency>
    /// ```
    /// and if you need to monitoring AWS S3 buckets, also include:
    /// ```
    /// <dependency>
    ///     <groupId>org.apache.flink</groupId>
    ///     <artifactId>flink-s3-fs-presto</artifactId>
    ///     <version>${flink.version}</version>
    /// </dependency>
    /// ```
    ///
    public static SingleOutputStreamOperator<Person> buildBoundedCSVStream(StreamExecutionEnvironment env, String filePath) {

        CsvReaderFormat<Person> personCsvReaderFormat = CsvReaderFormat.forPojo(Person.class);
        FileSource<Person> source = FileSource
                .forRecordStreamFormat(personCsvReaderFormat, new Path(filePath))
                .monitorContinuously(Duration.ofSeconds(10)) // Enable continuous monitoring for new files, means that at every 10 seconds the source will check for new files added to the path
                .build();


        env.setParallelism(1);
        return env.fromSource(source, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "file-source");
    }
}
