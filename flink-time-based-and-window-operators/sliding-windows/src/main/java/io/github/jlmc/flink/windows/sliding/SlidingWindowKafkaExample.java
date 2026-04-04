package io.github.jlmc.flink.windows.sliding;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.Instant;

import static org.apache.flink.configuration.ExternalizedCheckpointRetention.RETAIN_ON_CANCELLATION;

/**
 * Real-world example using Sliding Windows for Access Security detection.
 * If a user fails the password 5 times in 30 seconds, it's likely a bot.
 * We use a sliding window of 30s that slides every 5s.
 */
public class SlidingWindowKafkaExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        ParameterTool parameters = ParameterTool.fromArgs(args);
        String bootstrapServers = parameters.get("bootstrap.servers", "kafka:19092");
        String inputTopic = parameters.get("input.topic", "access-attempts");
        String outputTopic = parameters.get("output.topic", "access-alerts");
        int parallelism = parameters.getInt("parallelism", 1);
        int checkpointInterval = parameters.getInt("checkpoint.interval", 10_000);

        // Configura o paralelismo do job. Define quantas instâncias paralelas de cada operador serão executadas.
        // O valor padrão é 1, mas pode ser configurado via argumento --parallelism.
        env.setParallelism(parallelism);
        // Ativa e configura o mecanismo de checkpointing para tolerância a falhas.
        // O valor padrão de 10s é equilibrado, mas para latência ultra-baixa pode ser aumentado
        // (ex: 60s) para reduzir o overhead, ou diminuído para recuperação mais rápida.
        configureCheckpointing(env, checkpointInterval);

        KafkaSource<AccessEvent> source = KafkaSource.<AccessEvent>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("sliding-windows-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(
                        AccessEvent.class,
                        () -> {
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(new JavaTimeModule());
                            return objectMapper;
                        }
                ))
                .build();

        JsonSerializationSchema<AccessAlert> serializationSchema = new JsonSerializationSchema<>(
                () -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.registerModule(new JavaTimeModule());
                    return objectMapper;
                }
        );

        KafkaSink<AccessAlert> sink = KafkaSink.<AccessAlert>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(outputTopic)
                                .setKeySerializationSchema(new SerializationSchema<AccessAlert>() {
                                    @Override
                                    public byte[] serialize(AccessAlert element) {
                                        return element.userId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                    }
                                })
                                .setValueSerializationSchema(serializationSchema)
                                .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        execute(env, source, sink);
    }

    public static void execute(StreamExecutionEnvironment env,
                               org.apache.flink.api.connector.source.Source<AccessEvent, ?, ?> source,
                               org.apache.flink.api.connector.sink2.Sink<AccessAlert> sink) throws Exception {

        DataStream<AccessEvent> accessStream = env.fromSource(source, createWatermarkStrategy(), "Access Source");

        definePipeline(accessStream).sinkTo(sink);

        env.execute("Flink Sliding Window Access Security Example");
    }

    /**
     * Configura o mecanismo de Checkpointing do Flink.
     * O Checkpointing é fundamental para a tolerância a falhas, permitindo que o job recupere
     * seu estado interno em caso de falha de um TaskManager.
     *
     * @param env O ambiente de execução do stream.
     * @param checkpointInterval O intervalo em milissegundos entre o início de cada checkpoint.
     */
    private static void configureCheckpointing(StreamExecutionEnvironment env, int checkpointInterval) {
        if (checkpointInterval > 0) {
            // Ativa o checkpointing com o intervalo fornecido.
            env.enableCheckpointing(checkpointInterval);
            CheckpointConfig config = env.getCheckpointConfig();

            // Define o modo de consistência para EXACTLY_ONCE, garantindo que os dados
            // sejam processados como se não tivesse ocorrido nenhuma falha.
            //config.setCheckpointingConsistencyMode(org.apache.flink.core.execution.CheckpointingMode.EXACTLY_ONCE);
            config.setCheckpointingConsistencyMode(org.apache.flink.core.execution.CheckpointingMode.AT_LEAST_ONCE);

            // Garante um tempo mínimo de repouso entre os checkpoints para evitar sobrecarga no sistema.
            // Se o checkpoint demorar, o próximo só começará após este intervalo.
            // Aumentamos para 5s (5000ms) para dar mais tempo ao processamento real.
            config.setMinPauseBetweenCheckpoints(5_000);

            // Define o tempo máximo que um checkpoint pode demorar antes de ser cancelado.
            config.setCheckpointTimeout(60_000);

            // Permite apenas um checkpoint em progresso por vez.
            config.setMaxConcurrentCheckpoints(1);

            // Ativa o checkpoint não alinhado (Unaligned Checkpoints).
            // Isso reduz drasticamente a latência em situações de backpressure, pois as barreiras
            // de checkpoint não precisam esperar pelo alinhamento de todos os canais de entrada.
            // É ideal para janelas deslizantes que podem acumular muitos dados.
            config.enableUnalignedCheckpoints();

            // Mantém o checkpoint persistido mesmo após o cancelamento do job (útil para recuperação manual).
            config.setExternalizedCheckpointRetention(RETAIN_ON_CANCELLATION);
        }
    }

    /**
     * Define a estratégia de Watermark para o processamento de Event Time.
     * O Watermark é o mecanismo do Flink para lidar com atrasos nos eventos e progredir o tempo do sistema.
     *
     * @return Uma estratégia de Watermark configurada.
     */
    public static WatermarkStrategy<AccessEvent> createWatermarkStrategy() {
        return WatermarkStrategy
                // Permite que eventos cheguem com até 2 segundos de atraso em relação ao watermark atual.
                .<AccessEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                // Extrai o timestamp do campo 'timestamp' do evento para uso nas janelas de tempo.
                .withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli())
                // MUITO IMPORTANTE: Marca a partição/fonte como ociosa se não receber eventos por 10 segundos.
                // Isso permite que o Watermark avance mesmo que algumas partições do Kafka não tenham dados.
                .withIdleness(Duration.ofSeconds(10));
    }

    /**
     * Define a lógica central do pipeline de processamento.
     * Isolar a lógica neste método permite que ela seja testada de forma independente
     * (ex: usando fontes de dados em memória nos testes unitários).
     *
     * @param accessStream O fluxo de entrada de eventos de acesso.
     * @return Um fluxo de alertas de segurança gerados.
     */
    public static DataStream<AccessAlert> definePipeline(DataStream<AccessEvent> accessStream) {
        // Sliding window de 30s que "desliza" a cada 5s
        return accessStream
                // 1. Filtra apenas tentativas de login que falharam.
                .filter(event -> {
                    if (!event.success) {
                        System.out.printf("[INFO] Falha de login detectada para utilizador: %s às %s%n", event.userId, event.timestamp);
                        return true;
                    }
                    return false;
                })
                // 2. Agrupa os eventos pelo ID do utilizador para análise individual.
                .keyBy(event -> event.userId)
                // 3. Aplica uma janela deslizante (Sliding Window).
                .window(SlidingEventTimeWindows.of(Duration.ofSeconds(30), Duration.ofSeconds(5)))
                // 4. Processa os eventos na janela para contar falhas e gerar alertas.
                .process(new BotDetectionProcessFunction())
                // 5. Apenas dispara alertas se houver 5 ou mais falhas no intervalo de 30s.
                .filter(alert -> {
                    if (alert.failedAttempts >= 5) {
                        System.out.printf("[ALERTA] Bot detectado! Utilizador: %s, Falhas: %d na janela %s - %s%n",
                                alert.userId, alert.failedAttempts, alert.windowStart, alert.windowEnd);
                        return true;
                    }
                    return false;
                });
    }

    public static class BotDetectionProcessFunction extends ProcessWindowFunction<AccessEvent, AccessAlert, String, TimeWindow> {
        @Override
        public void process(String userId,
                            Context context,
                            Iterable<AccessEvent> elements,
                            Collector<AccessAlert> out) {
            long count = 0;
            for (AccessEvent ignored : elements) {
                count++;
            }

            out.collect(new AccessAlert(
                    userId,
                    count,
                    Instant.ofEpochMilli(context.window().getStart()),
                    Instant.ofEpochMilli(context.window().getEnd())
            ));
        }
    }
}
