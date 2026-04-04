package io.github.jlmc.flink.windows.tumbling;

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

public class TumblingWindowKafkaExampleTest {

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
    public void testTumblingWindowPipeline() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        // Dados de teste
        Instant baseTime = Instant.parse("2026-04-04T00:00:00Z");
        List<SensorReading> readings = new ArrayList<>();
        // Janela 1: [00:00:00 - 00:00:10]
        readings.add(new SensorReading("sensor1", baseTime.plusSeconds(1), 20.0));
        readings.add(new SensorReading("sensor1", baseTime.plusSeconds(2), 22.0));
        // Janela 2: [00:00:10 - 00:00:20]
        readings.add(new SensorReading("sensor1", baseTime.plusSeconds(11), 30.0));

        DataStream<SensorReading> sensorStream = env.fromData(readings)
                .assignTimestampsAndWatermarks(TumblingWindowKafkaExample.createWatermarkStrategy());

        TumblingWindowKafkaExample.definePipeline(sensorStream)
                .addSink(new CollectSink<>());

        env.execute();

        List<WindowResult> values = CollectSink.values();
        assertThat(values).hasSize(2);

        // Verificar janela 1
        WindowResult res1 = values.stream()
                .filter(r -> r.start.equals(baseTime))
                .findFirst()
                .orElseThrow();
        assertThat(res1.average).isEqualTo(21.0);
        assertThat(res1.measurementsCount).isEqualTo(2);

        // Verificar janela 2
        WindowResult res2 = values.stream()
                .filter(r -> r.start.equals(baseTime.plusSeconds(10)))
                .findFirst()
                .orElseThrow();
        assertThat(res2.average).isEqualTo(30.0);
        assertThat(res2.measurementsCount).isEqualTo(1);
    }
}
