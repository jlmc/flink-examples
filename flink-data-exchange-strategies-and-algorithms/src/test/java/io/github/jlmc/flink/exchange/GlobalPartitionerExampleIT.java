package io.github.jlmc.flink.exchange;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
public class GlobalPartitionerExampleIT {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.2").asCompatibleSubstituteFor("confluentinc/cp-kafka"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_BROKER_ID", "1")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");

    private static final String INPUT_TOPIC = "distributed-events";
    private static final String OUTPUT_TOPIC = "global-summary";

    static {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            String userHome = System.getProperty("user.home");
            String[] commonSockets = {
                    userHome + "/.docker/run/docker.sock",
                    "/var/run/docker.sock",
                    userHome + "/Library/Containers/com.docker.docker/Data/docker-cli.sock"
            };

            for (String socketPath : commonSockets) {
                if (new java.io.File(socketPath).exists()) {
                    TestcontainersConfiguration.getInstance().updateUserConfig("docker.host", "unix://" + socketPath);
                    break;
                }
            }
        }
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            adminClient.createTopics(List.of(
                    new NewTopic(INPUT_TOPIC, 4, (short) 1), // 4 partitions source
                    new NewTopic(OUTPUT_TOPIC, 1, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldFunnelAllDataIntoSubtaskZeroUsingGlobal() throws Exception {
        // 1. Produce data to input topic (across 4 partitions)
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        int messageCount = 50;
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < messageCount; i++) {
                // Send to different partitions
                producer.send(new ProducerRecord<>(INPUT_TOPIC, i % 4, "key-" + i, "event-" + i));
            }
            producer.flush();
        }

        // 2. Start Flink job
        System.setProperty("brokers", KAFKA.getBootstrapServers());
        System.setProperty("input-topic", INPUT_TOPIC);
        System.setProperty("output-topic", OUTPUT_TOPIC);

        GlobalPartitionerExample.execute(new String[0]);

        // 3. Consume from output topic and verify
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(OUTPUT_TOPIC));

            List<String> messages = new ArrayList<>();
            Set<Integer> subtaskIndices = new HashSet<>();

            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(100)).forEach(record -> {
                    String value = record.value();
                    messages.add(value);
                    // Extract subtaskIndex from JSON: {"value": "...", "subtaskIndex": N}
                    if (value.contains("\"subtaskIndex\":")) {
                        String indexStr = value.substring(value.lastIndexOf(":") + 1, value.lastIndexOf("}")).trim();
                        subtaskIndices.add(Integer.parseInt(indexStr));
                    }
                });

                assertTrue(messages.size() >= messageCount, "Expected at least " + messageCount + " messages. Received: " + messages.size());
                // Verify that ONLY subtask 0 was involved
                assertEquals(1, subtaskIndices.size(), "Expected data to be processed by exactly one subtask. Subtasks found: " + subtaskIndices);
                assertTrue(subtaskIndices.contains(0), "Expected subtask 0 to process all data. Found: " + subtaskIndices);
                System.out.println("[DEBUG_LOG] Subtasks involved: " + subtaskIndices);
            });
        }
    }
}
