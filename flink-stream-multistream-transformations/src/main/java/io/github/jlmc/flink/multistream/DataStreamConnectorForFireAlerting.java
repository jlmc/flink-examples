package io.github.jlmc.flink.multistream;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoMapFunction;

/**
 * To run this Flink example on macOS/Linux:
 * * <ol>
 * <li>Open a terminal and start the Temperature socket:
 * {@code nc -lk 9999}
 * </li>
 * <li>Open a second terminal and start the Smoke socket:
 * {@code nc -lk 9998}
 * </li>
 * <li>Run this class.</li>
 * <li>Input doubles (e.g., 45.0) in terminal 1 and strings (e.g., "YES") in terminal 2.</li>
 * </ol>
 * * Note: If you receive a "Connection Refused" error, ensure the {@code nc}
 * commands are running before starting the Flink job.
 */
public class DataStreamConnectorForFireAlerting {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        var temperatureDs = env.socketTextStream("localhost", 9999)
                        .map(Double::parseDouble);

        var smokeDs = env.socketTextStream("localhost", 9998)
                .map(String::toUpperCase);

        defineWorkflow(temperatureDs, smokeDs)
                        .print();

        env.execute("DataStream Connector For Fire Alerting");
    }

    public static SingleOutputStreamOperator<ForestMonitorData> defineWorkflow(
            org.apache.flink.streaming.api.datastream.DataStream<Double> temperatureDs,
            org.apache.flink.streaming.api.datastream.DataStream<String> smokeDs) {
        return temperatureDs.connect(smokeDs)
                .map(new CoMapFunction<Double, String, ForestMonitorData>() {
                    @Override
                    public ForestMonitorData map1(Double value) throws Exception {
                        return new ForestMonitorData(
                                ForestMonitorData.TYPE_TEMPERATURE,
                                null,
                                value
                        );
                    }

                    @Override
                    public ForestMonitorData map2(String value) throws Exception {
                        return new ForestMonitorData(
                                ForestMonitorData.TYPE_SMOKE,
                                value,
                                0.0
                        );
                    }
                }, Types.POJO(ForestMonitorData.class))
                .filter(ForestMonitorData::isFireAlert);
    }
}
