package io.github.jlmc.flink.transformations;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/**
 * Example of KeyedProcessFunction: Inactivity Alert.
 */
public class KeyedProcessFunctionExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<SensorReading> readings = env.fromElements(
                new SensorReading("sensor_1", 1000L, 20.0),
                new SensorReading("sensor_2", 2000L, 22.0),
                new SensorReading("sensor_1", 5000L, 21.0)
                // If sensor_1 doesn't send data for 10s, alert
        );

        DataStream<String> alerts = readings
                .keyBy(r -> r.id)
                .process(new InactivityAlertFunction());

        alerts.print("Alerts");

        env.execute("KeyedProcessFunction Example");
    }

    public static class InactivityAlertFunction extends KeyedProcessFunction<String, SensorReading, String> {

        private ValueState<Long> timerState;

        @Override
        public void open(org.apache.flink.api.common.functions.OpenContext parameters) {
            timerState = getRuntimeContext().getState(new ValueStateDescriptor<>("timer-state", Types.LONG));
        }

        @Override
        public void processElement(SensorReading value, Context ctx, Collector<String> out) throws Exception {
            long currentTime = ctx.timerService().currentProcessingTime();
            long timeout = currentTime + 10_000;

            Long lastTimer = timerState.value();
            if (lastTimer != null) {
                ctx.timerService().deleteProcessingTimeTimer(lastTimer);
            }

            ctx.timerService().registerProcessingTimeTimer(timeout);
            timerState.update(timeout);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            out.collect("Alert: Sensor " + ctx.getCurrentKey() + " has been inactive for 10s!");
            timerState.clear();
        }
    }

    public static class SensorReading {
        public String id;
        public long timestamp;
        public double temperature;

        public SensorReading() {}

        public SensorReading(String id, long timestamp, double temperature) {
            this.id = id;
            this.timestamp = timestamp;
            this.temperature = temperature;
        }

        @Override
        public String toString() {
            return "SensorReading{id='" + id + "', timestamp=" + timestamp + ", temperature=" + temperature + "}";
        }
    }
}
