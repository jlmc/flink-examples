package io.github.jlmc.flink.windows.sliding;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SlidingWindowKafkaExampleTest {

    static final MiniClusterWithClientResource FLINK_CLUSTER =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());

    @BeforeAll
    static void beforeAll() throws Exception {
        FLINK_CLUSTER.before();
    }

    @AfterAll
    static void afterAll() {
        FLINK_CLUSTER.after();
    }

    @BeforeEach
    void setUp() {
        CollectSink.clear();
    }

    @Test
    public void testSlidingWindowPipeline() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        // Dados de teste: Janelas de 30s, deslizam a cada 5s.
        // Simulamos 5 falhas para o mesmo utilizador num curto intervalo.
        Instant baseTime = Instant.parse("2026-04-04T00:00:00Z");
        List<AccessEvent> events = new ArrayList<>();

        // Utilizador 1: 5 falhas em 10 segundos
        events.add(new AccessEvent("user1", false, baseTime.plusSeconds(1)));
        events.add(new AccessEvent("user1", false, baseTime.plusSeconds(2)));
        events.add(new AccessEvent("user1", false, baseTime.plusSeconds(3)));
        events.add(new AccessEvent("user1", false, baseTime.plusSeconds(4)));
        events.add(new AccessEvent("user1", false, baseTime.plusSeconds(10)));

        // Utilizador 2: 3 falhas em 10 segundos (não deve gerar alerta)
        events.add(new AccessEvent("user2", false, baseTime.plusSeconds(5)));
        events.add(new AccessEvent("user2", false, baseTime.plusSeconds(6)));
        events.add(new AccessEvent("user2", false, baseTime.plusSeconds(7)));

        // Utilizador 3: 5 sucessos (não deve gerar alerta)
        events.add(new AccessEvent("user3", true, baseTime.plusSeconds(5)));
        events.add(new AccessEvent("user3", true, baseTime.plusSeconds(6)));
        events.add(new AccessEvent("user3", true, baseTime.plusSeconds(7)));
        events.add(new AccessEvent("user3", true, baseTime.plusSeconds(8)));
        events.add(new AccessEvent("user3", true, baseTime.plusSeconds(9)));

        DataStream<AccessEvent> accessStream = env.fromData(events)
                .assignTimestampsAndWatermarks(SlidingWindowKafkaExample.createWatermarkStrategy());

        SlidingWindowKafkaExample.definePipeline(accessStream)
                .addSink(new CollectSink<>());

        env.execute();

        List<AccessAlert> alerts = CollectSink.values();

        // Devemos ter alertas apenas para o user1.
        // Como é uma janela deslizante de 30s que desliza a cada 5s,
        // o user1 (que falhou aos 1s, 2s, 3s, 4s e 10s) aparecerá em várias janelas.
        // A primeira janela que contém todas as 5 falhas é a [00:00:00 - 00:00:30].
        // Mas janelas anteriores como [-25 - 05] só contêm as falhas de 1-4s (4 falhas), logo filtradas.

        assertThat(alerts).isNotEmpty();
        assertThat(alerts).allMatch(a -> a.userId.equals("user1"));
        assertThat(alerts).allMatch(a -> a.failedAttempts >= 5);

        // Verificar um alerta específico
        boolean foundExactAlert = alerts.stream()
                .anyMatch(a -> a.userId.equals("user1") && a.failedAttempts == 5);
        assertThat(foundExactAlert).isTrue();
    }
}
