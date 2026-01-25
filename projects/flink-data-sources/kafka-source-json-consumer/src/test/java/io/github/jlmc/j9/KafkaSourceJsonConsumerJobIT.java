package io.github.jlmc.j9;

import io.github.jlmc.j9.model.PersonLocationEvent;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
public class KafkaSourceJsonConsumerJobIT {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.2"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_BROKER_ID", "1")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");

    private static final String TOPIC = "person-location-events";

    @BeforeAll
    static void beforeAll() throws Exception {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            adminClient.createTopics(Collections.singletonList(new NewTopic(TOPIC, 1, (short) 1))).all().get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldConsumeDataFromKafka() throws Exception {
        // Produce some data to Kafka
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        String personId = "user-1";
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            String json = "{\"person_id\": \"" + personId + "\", \"latitude\": 42.3118, \"longitude\": -72.6882, \"event_timestamp\": 1769358411300}";
            producer.send(new ProducerRecord<>(TOPIC, json)).get();
        }

        // Set up Flink job
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);

        CollectSink.VALUES.clear();

        KafkaSourceJsonConsumerJob.createJob(env, KAFKA.getBootstrapServers())
                                  .addSink(new CollectSink());

        env.executeAsync("IT Job");

        // Verify the results
        await().atMost(Duration.ofSeconds(30))
               .untilAsserted(() -> {
                   assertEquals(1, CollectSink.VALUES.size());
                   assertEquals(personId, CollectSink.VALUES.get(0).personId());
               });
    }

    private static class CollectSink implements SinkFunction<PersonLocationEvent> {
        static final List<PersonLocationEvent> VALUES = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void invoke(PersonLocationEvent value, Context context) {
            VALUES.add(value);
        }
    }
}
