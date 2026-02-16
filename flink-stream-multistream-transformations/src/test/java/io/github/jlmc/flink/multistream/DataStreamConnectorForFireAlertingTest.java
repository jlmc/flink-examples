package io.github.jlmc.flink.multistream;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.AbstractTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataStreamConnectorForFireAlertingTest extends AbstractTestBase {

    @Test
    public void testFireAlertingWorkflow() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        CollectSink.values.clear();

        // 1. Prepare test data
        // Temperature > 50.0 should trigger alert
        DataStream<Double> temperatureDs = env.fromElements(45.0, 55.0, 30.0);
        
        // Smoke "MEDIUM" or "HIGH" should trigger alert
        DataStream<String> smokeDs = env.fromElements("LOW", "HIGH", "MEDIUM");

        // 2. Define workflow
        DataStream<ForestMonitorData> resultStream = DataStreamConnectorForFireAlerting.defineWorkflow(temperatureDs, smokeDs);

        // 3. Collect results
        resultStream.addSink(new CollectSink<ForestMonitorData>());

        // 4. Execute
        env.execute();

        // 5. Verify results
        // Expected alerts:
        // - Temperature 55.0
        // - Smoke HIGH
        // - Smoke MEDIUM
        assertThat(CollectSink.values).hasSize(3);
        
        assertThat(CollectSink.values)
                .filteredOn(o -> ((ForestMonitorData) o).type().equals(ForestMonitorData.TYPE_TEMPERATURE))
                .extracting(o -> ((ForestMonitorData) o).temperature())
                .containsExactlyInAnyOrder(55.0);

        assertThat(CollectSink.values)
                .filteredOn(o -> ((ForestMonitorData) o).type().equals(ForestMonitorData.TYPE_SMOKE))
                .extracting(o -> ((ForestMonitorData) o).smoke())
                .containsExactlyInAnyOrder("HIGH", "MEDIUM");
    }
}
