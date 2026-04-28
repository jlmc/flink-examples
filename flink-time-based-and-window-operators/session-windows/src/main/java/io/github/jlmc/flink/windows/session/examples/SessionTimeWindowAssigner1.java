package io.github.jlmc.flink.windows.session.examples;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.ProcessingTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;

public class SessionTimeWindowAssigner1 {

    private static final Duration DEFAULT_SESSION_GAP = Duration.ofMinutes(1);


    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStreamSource<String> stringDataStreamSource = createSocketSource(env, "localhost", 9090);

        definePipeline(stringDataStreamSource)
                .print();

        env.execute("window example 1");

    }

    static DataStreamSource<String> createSocketSource(StreamExecutionEnvironment env, String host, int port) {
        return env.socketTextStream(host, port);
    }

    static SingleOutputStreamOperator<Tuple2<String, Long>> definePipeline(DataStream<String> input) {
        return definePipeline(input, DEFAULT_SESSION_GAP);
    }

    static SingleOutputStreamOperator<Tuple2<String, Long>> definePipeline(DataStream<String> input, Duration sessionGap) {

        return input
                .map(line -> line.split(","))
                .map(parts -> new AppAccessLog(parts[0], parts[1], Long.parseLong(parts[2])), Types.POJO(AppAccessLog.class))
                .keyBy(AppAccessLog::getSessionId)
                .window(ProcessingTimeSessionWindows.withGap(sessionGap))
                .process(new ProcessWindowFunction<AppAccessLog, Tuple2<String, Long>, String, TimeWindow>() {
                    @Override
                    public void process(String sessionId,
                                        ProcessWindowFunction<AppAccessLog, Tuple2<String, Long>, String, TimeWindow>.Context context,
                                        Iterable<AppAccessLog> elements, Collector<Tuple2<String, Long>> out) {

                        long count = elements.spliterator().estimateSize();
                        out.collect(Tuple2.of(sessionId, count));
                    }
                });
    }

    public static class AppAccessLog {
        private String sessionId;
        private String uri;
        private long timestamp;

        public AppAccessLog() {
        }

        public AppAccessLog(String sessionId, String uri, long timestamp) {
            this.sessionId = sessionId;
            this.uri = uri;
            this.timestamp = timestamp;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "AppAccessLog{" +
                    "sessionId='" + sessionId + '\'' +
                    ", uri='" + uri + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
