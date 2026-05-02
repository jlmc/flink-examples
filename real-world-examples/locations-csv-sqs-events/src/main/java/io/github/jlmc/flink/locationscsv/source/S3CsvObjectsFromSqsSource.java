package io.github.jlmc.flink.locationscsv.source;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.Boundedness;
import org.apache.flink.api.connector.source.ReaderOutput;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.api.connector.source.SourceSplit;
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.core.io.InputStatus;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class S3CsvObjectsFromSqsSource
        implements Source<S3ObjectEvent, S3CsvObjectsFromSqsSource.SqsSplit, S3CsvObjectsFromSqsSource.SqsEnumeratorState>,
        ResultTypeQueryable<S3ObjectEvent> {

    private static final SqsSplit SINGLE_SPLIT = new SqsSplit("sqs-events-split");

    private final String endpoint;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String queueUrl;

    public S3CsvObjectsFromSqsSource(String endpoint, String region, String accessKey, String secretKey, String queueUrl) {
        this.endpoint = endpoint;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.queueUrl = queueUrl;
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SourceReader<S3ObjectEvent, SqsSplit> createReader(SourceReaderContext readerContext) {
        return new SqsSourceReader(endpoint, region, accessKey, secretKey, queueUrl, readerContext);
    }

    @Override
    public SplitEnumerator<SqsSplit, SqsEnumeratorState> createEnumerator(SplitEnumeratorContext<SqsSplit> enumContext) {
        return new SqsSplitEnumerator(enumContext, false);
    }

    @Override
    public SplitEnumerator<SqsSplit, SqsEnumeratorState> restoreEnumerator(
            SplitEnumeratorContext<SqsSplit> enumContext,
            SqsEnumeratorState checkpoint) {
        return new SqsSplitEnumerator(enumContext, checkpoint.splitAssigned());
    }

    @Override
    public SimpleVersionedSerializer<SqsSplit> getSplitSerializer() {
        return new SqsSplitSerializer();
    }

    @Override
    public SimpleVersionedSerializer<SqsEnumeratorState> getEnumeratorCheckpointSerializer() {
        return new SqsEnumeratorStateSerializer();
    }

    @Override
    public TypeInformation<S3ObjectEvent> getProducedType() {
        return TypeInformation.of(S3ObjectEvent.class);
    }

    public record SqsSplit(String splitId) implements SourceSplit {
    }

    public record SqsEnumeratorState(boolean splitAssigned) {
    }

    private static final class SqsSplitEnumerator implements SplitEnumerator<SqsSplit, SqsEnumeratorState> {

        private final SplitEnumeratorContext<SqsSplit> context;
        private boolean splitAssigned;

        private SqsSplitEnumerator(SplitEnumeratorContext<SqsSplit> context, boolean splitAssigned) {
            this.context = context;
            this.splitAssigned = splitAssigned;
        }

        @Override
        public void start() {
            tryAssignSplit();
        }

        @Override
        public void handleSplitRequest(int subtaskId, String requesterHostname) {
            if (!splitAssigned) {
                context.assignSplit(SINGLE_SPLIT, subtaskId);
                splitAssigned = true;
            }
        }

        @Override
        public void addSplitsBack(List<SqsSplit> splits, int subtaskId) {
            if (!splits.isEmpty()) {
                splitAssigned = false;
                tryAssignSplit();
            }
        }

        @Override
        public void addReader(int subtaskId) {
            tryAssignSplit();
        }

        @Override
        public SqsEnumeratorState snapshotState(long checkpointId) {
            return new SqsEnumeratorState(splitAssigned);
        }

        @Override
        public void close() {
            // nothing to close
        }

        private void tryAssignSplit() {
            if (splitAssigned) {
                return;
            }

            for (Map.Entry<Integer, ?> reader : context.registeredReaders().entrySet()) {
                context.assignSplit(SINGLE_SPLIT, reader.getKey());
                splitAssigned = true;
                break;
            }
        }
    }

    private static final class SqsSourceReader implements SourceReader<S3ObjectEvent, SqsSplit> {

        private final String queueUrl;
        private final SourceReaderContext context;

        private final SqsClient sqsClient;
        private final ObjectMapper objectMapper;
        private boolean splitAssigned;

        private SqsSourceReader(
                String endpoint,
                String region,
                String accessKey,
                String secretKey,
                String queueUrl,
                SourceReaderContext context
        ) {
            this.queueUrl = queueUrl;
            this.context = context;
            this.objectMapper = new ObjectMapper();
            this.sqsClient = SqsClient.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                    )
                    .build();
        }

        @Override
        public void start() {
            context.sendSplitRequest();
        }

        @Override
        public InputStatus pollNext(ReaderOutput<S3ObjectEvent> output) throws Exception {
            if (!splitAssigned) {
                return InputStatus.NOTHING_AVAILABLE;
            }

            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(10)
                    .build();

            ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
            int emitted = 0;
            for (Message message : response.messages()) {
                emitted += emitFromMessage(output, message);
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            }

            return emitted > 0 ? InputStatus.MORE_AVAILABLE : InputStatus.NOTHING_AVAILABLE;
        }

        @Override
        public List<SqsSplit> snapshotState(long checkpointId) {
            return splitAssigned ? List.of(SINGLE_SPLIT) : List.of();
        }

        @Override
        public CompletableFuture<Void> isAvailable() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void addSplits(List<SqsSplit> splits) {
            splitAssigned = !splits.isEmpty();
        }

        @Override
        public void notifyNoMoreSplits() {
            // unbounded source: nothing to do
        }

        @Override
        public void close() {
            sqsClient.close();
        }

        private int emitFromMessage(ReaderOutput<S3ObjectEvent> output, Message message) throws Exception {
            JsonNode root = objectMapper.readTree(message.body());
            JsonNode records = root.get("Records");
            if (records == null || !records.isArray()) {
                return 0;
            }

            int emitted = 0;
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
                    output.collect(new S3ObjectEvent(bucket, key, eventName));
                    emitted++;
                }
            }

            return emitted;
        }
    }

    private static final class SqsSplitSerializer implements SimpleVersionedSerializer<SqsSplit> {

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public byte[] serialize(SqsSplit split) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(split.splitId());
            out.flush();
            return baos.toByteArray();
        }

        @Override
        public SqsSplit deserialize(int version, byte[] serialized) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(serialized));
            return new SqsSplit(in.readUTF());
        }
    }

    private static final class SqsEnumeratorStateSerializer implements SimpleVersionedSerializer<SqsEnumeratorState> {

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public byte[] serialize(SqsEnumeratorState state) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeBoolean(state.splitAssigned());
            out.flush();
            return baos.toByteArray();
        }

        @Override
        public SqsEnumeratorState deserialize(int version, byte[] serialized) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(serialized));
            return new SqsEnumeratorState(in.readBoolean());
        }
    }
}
