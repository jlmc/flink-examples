# CSV Sink and Source Connector Examples

This module demonstrates how to work with CSV data in Apache Flink using `FileSink` for writing and `FileSource` with `CsvReaderFormat` for reading. It uses `JacksonCsvEncoder` from the common module to ensure robust and consistent CSV serialization.

The primary classes in this module are:
- `CsvFileSinkConnectorExample.java`: Demonstrates writing structured data (POJOs) in CSV format.
- `CsvFileSourceConnectorExample.java`: Demonstrates reading structured data (POJOs) in CSV format using `CsvReaderFormat`.
- `PersonCsvFileSinkConnectorExample.java`: Demonstrates writing `Person` records (name, age) in CSV format.
- `PersonCsvFileSourceConnectorExample.java`: Demonstrates reading `Person` records from a CSV file into POJOs.

## Examples

### CSV File Sink Example

This example uses `FileSink` with `JacksonCsvEncoder` to write `Patient` POJOs to `/tmp/flink-csv-output` in proper CSV format.

```java
FileSink<Patient> csvSink = FileSink
        .forRowFormat(new Path(outputDirectory), new JacksonCsvEncoder<>(Patient.class))
        .withBucketAssigner(new DateTimeBucketAssigner<>("yyyy-MM-dd-HH-mm"))
        .withOutputFileConfig(OutputFileConfig.builder()
                .withPartPrefix("patients")
                .withPartSuffix(".csv")
                .build())
        .build();
```

### CSV File Source Example

This example uses `FileSource` and `CsvReaderFormat` to read `Patient` POJOs from `/tmp/flink-csv-output`.

```java
CsvReaderFormat<Patient> csvFormat = CsvReaderFormat.forPojo(Patient.class);

FileSource<Patient> source = 
        FileSource.forRecordStreamFormat(csvFormat, new Path(inputPath))
                .monitorContinuously(Duration.ofSeconds(5))
                .build();
```

### Person CSV File Examples

These examples demonstrate writing and reading `Person` objects (name, age) using `/tmp/flink-person-csv-output`.

**Writing:**
```java
FileSink<Person> sink = FileSink
        .forRowFormat(new Path(path), new JacksonCsvEncoder<>(Person.class))
        .build();
```

**Reading:**
```java
CsvReaderFormat<Person> csvFormat = CsvReaderFormat.forPojo(Person.class);
FileSource<Person> source = FileSource.forRecordStreamFormat(csvFormat, new Path(inputPath)).build();
```

## Running the Examples

1. Build the module:
   ```sh
   mvn clean install -DskipTests
   ```

2. Run the CSV sink example:
   ```sh
   mvn exec:java -Dexec.mainClass="io.github.jlmc.flink.sinks.CsvFileSinkConnectorExample"
   ```

3. Run the CSV source example:
   ```sh
   mvn exec:java -Dexec.mainClass="io.github.jlmc.flink.sinks.CsvFileSourceConnectorExample"
   ```

4. Run the Person CSV sink example:
   ```sh
   mvn exec:java -Dexec.mainClass="io.github.jlmc.flink.sinks.PersonCsvFileSinkConnectorExample"
   ```

5. Run the Person CSV source example:
   ```sh
   mvn exec:java -Dexec.mainClass="io.github.jlmc.flink.sinks.PersonCsvFileSourceConnectorExample"
   ```

## Documentation

For more information, see the [Flink File System documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/filesystem/).
