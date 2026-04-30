package io.github.jlmc.flink.watermarks.outoforderness;

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

public class OutOfOrdernessTimestampKafkaExampleTest {

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
    void testOutOfOrdernessPipeline() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        Instant baseTime = Instant.parse("2026-04-04T00:00:00Z");
        List<SensorReading> readings = new ArrayList<>();

        readings.add(new SensorReading("sensor1", baseTime.plusSeconds(1), 20.0));
        readings.add(new SensorReading("sensor1", baseTime.plusSeconds(12), 30.0));
        readings.add(new SensorReading("sensor1", baseTime.plusSeconds(2), 22.0));
        readings.add(new SensorReading("sensor1", baseTime.plusSeconds(25), 40.0));

        DataStream<SensorReading> sensorStream = env.fromData(readings)
                .assignTimestampsAndWatermarks(OutOfOrdernessTimestampKafkaExample.createWatermarkStrategy());

        OutOfOrdernessTimestampKafkaExample.definePipeline(sensorStream)
                .addSink(new CollectSink<>());

        env.execute();

        List<WindowResult> values = CollectSink.values();
        assertThat(values).hasSize(3);

        WindowResult firstWindow = values.stream()
                .filter(r -> r.start.equals(baseTime))
                .findFirst()
                .orElseThrow();
        assertThat(firstWindow.average).isEqualTo(21.0);
        assertThat(firstWindow.measurementsCount).isEqualTo(2);

        WindowResult secondWindow = values.stream()
                .filter(r -> r.start.equals(baseTime.plusSeconds(10)))
                .findFirst()
                .orElseThrow();
        assertThat(secondWindow.average).isEqualTo(30.0);
        assertThat(secondWindow.measurementsCount).isEqualTo(1);

        WindowResult thirdWindow = values.stream()
                .filter(r -> r.start.equals(baseTime.plusSeconds(20)))
                .findFirst()
                .orElseThrow();
        assertThat(thirdWindow.average).isEqualTo(40.0);
        assertThat(thirdWindow.measurementsCount).isEqualTo(1);
    }
}
