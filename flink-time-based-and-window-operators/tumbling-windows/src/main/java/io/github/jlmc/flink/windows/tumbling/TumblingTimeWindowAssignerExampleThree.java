package io.github.jlmc.flink.windows.tumbling;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.Serializable;
import java.time.Duration;

import static org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows.of;

public class TumblingTimeWindowAssignerExampleThree {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        ParameterTool parameters = ParameterTool.fromArgs(args);

        String host = parameters.get("socket.host", "localhost");
        int port = parameters.getInt("socket.port", 9999);

        DataStreamSource<String> dataSource = createSocketSource(env, host, port);

        definePipeline(dataSource)
                .print();

        env.execute("Tumbling Time Window Assigner Example Two");

    }

    static DataStreamSource<String> createSocketSource(StreamExecutionEnvironment env, String host, int port) {
        return env.socketTextStream(host, port);
    }

    static SingleOutputStreamOperator<CityTemperature> definePipeline(DataStream<String> input) {
        return input
                .map(line -> {
                    String[] fields = line.split(",");


                    return new CityTemperature(
                            fields[0], // city
                            Float.parseFloat(fields[1]), // temperature
                            Long.parseLong(fields[2]) // timestamp
                    );

                }, Types.GENERIC(CityTemperature.class))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<CityTemperature>forMonotonousTimestamps()
                                .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
                )
                .keyBy(CityTemperature::getCity)
                .window(of(Duration.ofMinutes(1L)))
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

        public long getTimestamp() {
            return timestamp;
        }
    }

}
