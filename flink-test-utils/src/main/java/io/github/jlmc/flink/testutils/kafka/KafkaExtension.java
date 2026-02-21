package io.github.jlmc.flink.testutils.kafka;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.TestcontainersConfiguration;

/**
 * JUnit 5 extension that starts a {@link KafkaContainer} using Testcontainers.
 * It also sets the "brokers" system property to the bootstrap servers of the started container.
 * This extension ensures that a single Kafka container is shared across all test classes
 * that use it in the same test suite.
 *
 * <p>Usually used via {@link EnableKafka @EnableKafka} meta-annotation:
 * <pre>{@code
 * @EnableKafka
 * class MyTest { ... }
 * }</pre>
 *
 * <p>But can also be used directly:
 * <pre>{@code
 * @ExtendWith(KafkaExtension.class)
 * class MyTest { ... }
 * }</pre>
 */
public class KafkaExtension implements BeforeAllCallback, AfterAllCallback {

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.2").asCompatibleSubstituteFor("confluentinc/cp-kafka"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_BROKER_ID", "1")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");

    static {
        // macOS logic to ensure Testcontainers can find the Docker socket
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

    /**
     * Starts the Kafka container if it is not already running.
     * Sets the "brokers" system property.
     * @param context the extension context
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        if (!KAFKA.isRunning()) {
            KAFKA.start();
            System.setProperty("brokers", KAFKA.getBootstrapServers());
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // We leave the KAFKA container running for other tests that might use it,
        // or we can stop it if this is the only test.
        // But since it's static, it's common practice to let Ryuk handle it.
        // However, if we want to be explicit:
        // KAFKA.stop();
    }

    /**
     * Provides the bootstrap servers address for the Kafka container.
     * @return the bootstrap servers address
     */
    public static String getBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }
}
