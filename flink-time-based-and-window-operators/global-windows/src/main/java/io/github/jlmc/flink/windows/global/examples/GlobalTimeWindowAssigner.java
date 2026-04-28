package io.github.jlmc.flink.windows.global.examples;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;
import org.apache.flink.streaming.api.windowing.triggers.PurgingTrigger;

import java.io.Serializable;

public class GlobalTimeWindowAssigner {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStreamSource<String> source = createSocketSource(env, "localhost", 9090);

        definePipeline(source)
                .print()
        ;

        env.execute("global window");
    }

    static DataStreamSource<String> createSocketSource(StreamExecutionEnvironment env, String host, int port) {
        return env.socketTextStream(host, port);
    }

    static SingleOutputStreamOperator<CityTemperature> definePipeline(DataStream<String> input) {
        return input.map(line -> line.split(","))
                .map(parts -> new CityTemperature(
                                parts[0],
                                Float.parseFloat(parts[1]),
                                Long.parseLong(parts[2])),
                        Types.POJO(CityTemperature.class)
                )
                .keyBy(CityTemperature::getCity)
                .window(GlobalWindows.create())
                .trigger(
                        PurgingTrigger.of(CountTrigger.of(5))
                )
                .max("temperature");
    }

    public static class CityTemperature implements Serializable {
        private String city;
        private float temperature;
        private long timestamp;

        public CityTemperature() {
        }

        public CityTemperature(String city, float temperature, long timestamp) {
            this.city = city;
            this.temperature = temperature;
            this.timestamp = timestamp;
        }

        public String getCity() {
            return city;
        }

        public float getTemperature() {
            return temperature;
        }

        public void setTemperature(float temperature) {
            this.temperature = temperature;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public void setCity(String city) {
            this.city = city;
        }

        @Override
        public String toString() {
            return "CityTemperature{" +
                    "city='" + city + '\'' +
                    ", temperature=" + temperature +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
