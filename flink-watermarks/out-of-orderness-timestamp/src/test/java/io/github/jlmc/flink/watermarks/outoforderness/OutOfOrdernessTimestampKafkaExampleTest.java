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
        List<FhirAdtEvent> readings = new ArrayList<>();

        readings.add(new FhirAdtEvent("msg-1", "pat-1", "hospital-lisbon", "ADT_A01", baseTime.plusSeconds(1)));
        readings.add(new FhirAdtEvent("msg-2", "pat-2", "hospital-lisbon", "ADT_A03", baseTime.plusSeconds(12)));
        readings.add(new FhirAdtEvent("msg-3", "pat-3", "hospital-lisbon", "ADT_A01", baseTime.plusSeconds(2)));
        readings.add(new FhirAdtEvent("msg-4", "pat-4", "hospital-lisbon", "ADT_A03", baseTime.plusSeconds(25)));

        DataStream<FhirAdtEvent> sensorStream = env.fromData(readings)
                .assignTimestampsAndWatermarks(OutOfOrdernessTimestampKafkaExample.createWatermarkStrategy());

        OutOfOrdernessTimestampKafkaExample.definePipeline(sensorStream)
                .addSink(new CollectSink<>());

        env.execute();

        List<AdtWindowResult> values = CollectSink.values();
        assertThat(values).hasSize(3);

        AdtWindowResult firstWindow = values.stream()
                .filter(r -> r.start.equals(baseTime))
                .findFirst()
                .orElseThrow();
        assertThat(firstWindow.facilityId).isEqualTo("hospital-lisbon");
        assertThat(firstWindow.eventType).isEqualTo("ADT_A01");
        assertThat(firstWindow.totalEvents).isEqualTo(2);
        assertThat(firstWindow.admits).isEqualTo(2);
        assertThat(firstWindow.discharges).isEqualTo(0);
        assertThat(firstWindow.transfers).isEqualTo(0);

        AdtWindowResult secondWindow = values.stream()
                .filter(r -> r.start.equals(baseTime.plusSeconds(10)))
                .findFirst()
                .orElseThrow();
        assertThat(secondWindow.facilityId).isEqualTo("hospital-lisbon");
        assertThat(secondWindow.eventType).isEqualTo("ADT_A03");
        assertThat(secondWindow.totalEvents).isEqualTo(1);
        assertThat(secondWindow.admits).isEqualTo(0);
        assertThat(secondWindow.discharges).isEqualTo(1);
        assertThat(secondWindow.transfers).isEqualTo(0);

        AdtWindowResult thirdWindow = values.stream()
                .filter(r -> r.start.equals(baseTime.plusSeconds(20)))
                .findFirst()
                .orElseThrow();
        assertThat(thirdWindow.facilityId).isEqualTo("hospital-lisbon");
        assertThat(thirdWindow.eventType).isEqualTo("ADT_A03");
        assertThat(thirdWindow.totalEvents).isEqualTo(1);
        assertThat(thirdWindow.admits).isEqualTo(0);
        assertThat(thirdWindow.discharges).isEqualTo(1);
        assertThat(thirdWindow.transfers).isEqualTo(0);
    }
}
