package io.github.jlmc.flink.windows.tumbling;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.time.Duration;

import static org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows.of;

public class TumblingTimeWindowAssignerExampleTwo {

    private static final String FORMATTER = "HH:mm:ss.SSS";

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

    static SingleOutputStreamOperator<String> definePipeline(DataStream<String> input) {

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
                .process(new ProcessWindowFunction<CityTemperature, String, String, TimeWindow>() {

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                    }

                    @Override
                    public void process(String key,
                                        ProcessWindowFunction<CityTemperature, String, String, TimeWindow>.Context context,
                                        Iterable<CityTemperature> elements, Collector<String> out) {
                        StringBuilder builder = new StringBuilder();

                        TimeWindow window = context.window();

                        long start = window.getStart();
                        long end = window.getEnd();
                        long lng = window.maxTimestamp();

                        String startFormatted = DateFormatUtils.format(start, FORMATTER);
                        String endFormatted = DateFormatUtils.format(end, FORMATTER);
                        String lngFormatted = DateFormatUtils.format(lng, FORMATTER);

                        StringBuilder appended = builder.append("key:")
                                .append(key)
                                .append(", [").append(startFormatted)
                                .append(" - ")
                                .append(endFormatted)
                                .append("]")
                                .append(", maxTimestamp: ").append(lngFormatted)
                                .append(", count: ").append(elements.spliterator().estimateSize());

                        out.collect(appended.toString());
                    }
                });
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
