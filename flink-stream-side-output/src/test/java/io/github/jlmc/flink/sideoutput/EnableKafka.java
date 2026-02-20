package io.github.jlmc.flink.sideoutput;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that enables Kafka support for JUnit 5 tests.
 * This annotation uses {@link KafkaExtension} to manage the lifecycle of a Kafka container
 * using Testcontainers.
 *
 * <p>Example usage:
 * <pre>{@code
 * @EnableKafka
 * class MyKafkaIT {
 *     @Test
 *     void testWithKafka() {
 *         String brokers = KafkaExtension.getBootstrapServers();
 *         // or use the "brokers" system property
 *         String brokersProp = System.getProperty("brokers");
 *
 *         // setup Kafka producer/consumer and run tests
 *     }
 * }
 * }</pre>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(KafkaExtension.class)
public @interface EnableKafka {
}
