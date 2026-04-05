package io.github.jlmc.flink.windows;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.configuration.WebOptions;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class WindowOperatorExamples {

    public static void main(String[] args) throws Exception {
        try {

            Configuration conf = new Configuration() {
                {
                    set(RestOptions.PORT, 8081);
                    set(RestOptions.BIND_PORT, "8081-8099");
                    set(TaskManagerOptions.NUM_TASK_SLOTS, 2);
                    set(TaskManagerOptions.MANAGED_MEMORY_SIZE, MemorySize.parse("256mb"));
                    set(WebOptions.LOG_PATH, "./logs/flink.log");
                }
            };
           // StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


            run(env);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("InaccessibleObjectException") ||
                    (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("InaccessibleObjectException"))) {
                System.err.println("\n[ERROR] Erro de encapsulamento do JDK detectado!");
                System.err.println("[ERROR] Para corrigir, adicione os seguintes argumentos em 'VM Options' na configuração de execução da sua IDE:");
                System.err.println("\n--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/sun.net.util=ALL-UNNAMED\n");
            }
            throw e;
        }
    }

    private static void run(StreamExecutionEnvironment env) throws Exception {

        Source<Bet, ?, ?> source =
                new DataGeneratorSource<>(
                        new BetGeneratorFunction(),
                        Long.MAX_VALUE,
                        RateLimiterStrategy.perSecond(1.0),
                        org.apache.flink.api.common.typeinfo.TypeInformation.of(Bet.class)
                );

        DataStream<Bet> dataStream =
                env.fromSource(source, WatermarkStrategy.<Bet>forMonotonousTimestamps().withTimestampAssigner((bet, timestamp) -> bet.timestamp().toEpochMilli()), "Bets Source");

        definePipeline(dataStream)
                .map(bet -> String.format("User: %s, Sum: %.2f", bet.userId(), bet.value()))
                .print();

        env.execute("Window Operator Examples");
    }

    public static SingleOutputStreamOperator<UserBetTotal> definePipeline(DataStream<Bet> dataStream) {
        return dataStream
                .map(bet -> new UserBetTotal(bet.userId(), bet.value()))
                .keyBy(UserBetTotal::userId)
                .window(org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows.of(Time.seconds(10)))
                .reduce((t1, t2) -> new UserBetTotal(t1.userId(), t1.value() + t2.value()));
    }

    private static class BetGeneratorFunction implements GeneratorFunction<Long, Bet> {
        @Override
        public Bet map(Long value) {
            return new Bet(
                    "user" + (value % 3 + 1),
                    Instant.now(),
                    (value + 1) * 10.0,
                    new HashMap<>(Map.of("market", "football", "odds", "2.5"))
            );
        }
    }

    
    public record UserBetTotal(String userId, double value) implements Serializable {}
    public record Bet(String userId, Instant timestamp, double value, java.util.HashMap<String, String> marketData) implements Serializable {}
}
