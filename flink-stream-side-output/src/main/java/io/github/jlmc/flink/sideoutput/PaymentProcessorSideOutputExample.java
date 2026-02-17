package io.github.jlmc.flink.sideoutput;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

/// This example simulates a Payment Processor that validates transactions.
///
/// Valid transactions stay in the Main Stream, while invalid ones (negative amounts or zero) are diverted to a Dead Letter Queue (DLQ) side output.
public class PaymentProcessorSideOutputExample {

    // 1. Define the OutputTag for the Side Output.
    // We use a String tag to provide detailed error messages for the DLQ.
    public static final OutputTag<String> DLQ_TAG = new OutputTag<String>("dead-letter-queue"){};

    // Kafka Broker Configuration
    private static final String BROKERS = "localhost:9092";

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. Mock Input Source (Simulated raw input strings)
        DataStream<String> input = env.fromData("100.50", "corrupted_json", "-5.0", "200.0", "0.0");

        // 3. Main Processing and Routing Logic
        SingleOutputStreamOperator<String> mainStream = defineWorkflow(input);

        // 4. Configure Kafka Sink for SUCCESS (Topic: transactions-success)
        KafkaSink<String> successSink = KafkaSink.<String>builder()
                .setBootstrapServers(BROKERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("transactions-success")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        // 5. Configure Kafka Sink for ERRORS/DLQ (Topic: transactions-errors)
        KafkaSink<String> errorSink = KafkaSink.<String>builder()
                .setBootstrapServers(BROKERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("transactions-errors")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();


        // 6. Connect streams to their respective Kafka Sinks

        // Connect Success Stream
        mainStream.sinkTo(successSink);

        // Connect Error/DLQ Stream (Side Output)
        mainStream.getSideOutput(DLQ_TAG).sinkTo(errorSink);

        // Execute the Job
        env.execute("Flink Kafka Side Output DLQ Project");
    }

    public static SingleOutputStreamOperator<String> defineWorkflow(DataStream<String> input) {
        return input.process(new ProcessFunction<String, String>() {
            @Override
            public void processElement(String value, Context ctx, Collector<String> out) {
                try {
                    double amount = Double.parseDouble(value);

                    if (amount > 0) {
                        // SUCCESS: Forward to main flow (positive amounts)
                        out.collect("VALID_TRANSACTION: " + amount);
                    } else {
                        // BUSINESS ERROR: Negative or zero values go to Side Output
                        ctx.output(DLQ_TAG, "BUSINESS_EXCEPTION: Invalid amount (" + value + ")");
                    }
                } catch (NumberFormatException e) {
                    // TECHNICAL ERROR: Parsing failure goes to Side Output
                    ctx.output(DLQ_TAG, "TECHNICAL_EXCEPTION: Non-numeric data (" + value + ")");
                }
            }
        });
    }
}
