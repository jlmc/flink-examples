package io.github.jlmc.flink.sideoutput;

import io.github.jlmc.flink.testutils.kafka.EnableKafka;
import io.github.jlmc.flink.testutils.kafka.KafkaExtension;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKafka
public class PaymentProcessorSideOutputExampleIT {

    private static final String INPUT_TOPIC = "transaction";
    private static final String SUCCESS_TOPIC = "transactions-success";
    private static final String ERROR_TOPIC = "transactions-errors";

    @BeforeAll
    static void beforeAll() throws Exception {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaExtension.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            adminClient.createTopics(Arrays.asList(
                    new NewTopic(INPUT_TOPIC, 1, (short) 1),
                    new NewTopic(SUCCESS_TOPIC, 1, (short) 1),
                    new NewTopic(ERROR_TOPIC, 1, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldProcessTransactionsThroughKafka() throws Exception {
        // 1. Produce data to input topic
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaExtension.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            // Valid transaction
            producer.send(new ProducerRecord<>(INPUT_TOPIC, "{\"amount\": 100.50}"));
            // Business error (negative)
            producer.send(new ProducerRecord<>(INPUT_TOPIC, "{\"amount\": -5.0}"));
            // Technical error (non-numeric or missing amount)
            producer.send(new ProducerRecord<>(INPUT_TOPIC, "{\"amount\": \"invalid\"}"));
            producer.send(new ProducerRecord<>(INPUT_TOPIC, "corrupted_json"));
            producer.flush();
        }

        // 2. Start Flink job
        System.setProperty("brokers", KafkaExtension.getBootstrapServers());
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);

        // We run the main method in a separate thread because execute() is blocking, 
        // or we use executeAsync(). Since PaymentProcessorSideOutputExample.main calls env.execute(),
        // we can just call it here.
        new Thread(() -> {
            try {
                PaymentProcessorSideOutputExample.main(new String[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // 3. Consume from output topics and verify
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaExtension.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> successConsumer = new KafkaConsumer<>(consumerProps);
             KafkaConsumer<String, String> errorConsumer = new KafkaConsumer<>(consumerProps)) {
            
            successConsumer.subscribe(List.of(SUCCESS_TOPIC));
            errorConsumer.subscribe(List.of(ERROR_TOPIC));

            List<String> successMessages = new ArrayList<>();
            List<String> errorMessages = new ArrayList<>();

            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                ConsumerRecords<String, String> successRecords = successConsumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : successRecords) {
                    successMessages.add(record.value());
                }

                ConsumerRecords<String, String> errorRecords = errorConsumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : errorRecords) {
                    errorMessages.add(record.value());
                }

                assertTrue(successMessages.contains("VALID_TRANSACTION: 100.5"), "Missing success message. Received: " + successMessages);
                assertTrue(errorMessages.stream().anyMatch(m -> m.contains("BUSINESS_EXCEPTION") && m.contains("-5.0")), "Missing business exception. Received: " + errorMessages);
                assertTrue(errorMessages.stream().anyMatch(m -> m.contains("TECHNICAL_EXCEPTION")), "Missing technical exception. Received: " + errorMessages);
            });
        }
    }
}
