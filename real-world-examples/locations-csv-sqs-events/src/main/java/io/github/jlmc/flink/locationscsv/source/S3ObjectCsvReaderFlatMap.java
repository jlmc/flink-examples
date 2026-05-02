package io.github.jlmc.flink.locationscsv.source;

import io.github.jlmc.flink.locationscsv.domain.entity.Location;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class S3ObjectCsvReaderFlatMap extends RichFlatMapFunction<S3ObjectEvent, S3ObjectCsvReaderFlatMap.LocationWithSource> {

    private final String endpoint;
    private final String region;
    private final String accessKey;
    private final String secretKey;

    private transient S3Client s3Client;

    public S3ObjectCsvReaderFlatMap(String endpoint, String region, String accessKey, String secretKey) {
        this.endpoint = endpoint;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .forcePathStyle(true)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                )
                .build();
    }

    @Override
    public void flatMap(S3ObjectEvent event, Collector<LocationWithSource> out) throws Exception {
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(GetObjectRequest.builder()
                .bucket(event.bucket())
                .key(event.key())
                .build());
             BufferedReader reader = new BufferedReader(new InputStreamReader(response, StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            long fileLineNumber = 0;
            while ((line = reader.readLine()) != null) {
                fileLineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] tokens = line.split(",", -1);
                if (tokens.length < 4) {
                    continue;
                }

                Location location = new Location(
                        tokens[0].trim(),
                        Double.valueOf(tokens[1].trim()),
                        Double.valueOf(tokens[2].trim()),
                        tokens[3].trim()
                );
                out.collect(new LocationWithSource(location, event.key(), fileLineNumber, false));
            }

            out.collect(new LocationWithSource(null, event.key(), -1L, true));
        }
    }

    @Override
    public void close() throws Exception {
        if (s3Client != null) {
            s3Client.close();
        }
        super.close();
    }

    public record LocationWithSource(Location location, String sourceFilePath, long lineNumber, boolean endOfFile) implements Serializable {
    }
}
