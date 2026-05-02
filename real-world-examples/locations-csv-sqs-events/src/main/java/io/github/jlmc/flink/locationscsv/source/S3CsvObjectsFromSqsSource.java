package io.github.jlmc.flink.locationscsv.source;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class S3CsvObjectsFromSqsSource extends RichSourceFunction<S3ObjectEvent> {

    private final String endpoint;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String queueUrl;

    private transient SqsClient sqsClient;
    private transient ObjectMapper objectMapper;
    private volatile boolean running = true;

    public S3CsvObjectsFromSqsSource(String endpoint, String region, String accessKey, String secretKey, String queueUrl) {
        this.endpoint = endpoint;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.queueUrl = queueUrl;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        objectMapper = new ObjectMapper();
        sqsClient = SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                )
                .build();
    }

    @Override
    public void run(SourceContext<S3ObjectEvent> ctx) throws Exception {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(10)
                .build();

        while (running) {
            ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
            for (Message message : response.messages()) {
                emitFromMessage(ctx, message);
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            }
        }
    }

    private void emitFromMessage(SourceContext<S3ObjectEvent> ctx, Message message) throws Exception {
        JsonNode root = objectMapper.readTree(message.body());
        JsonNode records = root.get("Records");
        if (records == null || !records.isArray()) {
            return;
        }

        Iterator<JsonNode> iterator = records.iterator();
        while (iterator.hasNext()) {
            JsonNode eventNode = iterator.next();
            String eventName = eventNode.path("eventName").asText("");
            if (!eventName.startsWith("ObjectCreated")) {
                continue;
            }

            String bucket = eventNode.path("s3").path("bucket").path("name").asText("");
            String key = URLDecoder.decode(eventNode.path("s3").path("object").path("key").asText(""), StandardCharsets.UTF_8);

            if (!bucket.isBlank() && !key.isBlank() && key.endsWith(".csv")) {
                synchronized (ctx.getCheckpointLock()) {
                    ctx.collect(new S3ObjectEvent(bucket, key, eventName));
                }
            }
        }
    }

    @Override
    public void cancel() {
        running = false;
        if (sqsClient != null) {
            sqsClient.close();
        }
    }
}
