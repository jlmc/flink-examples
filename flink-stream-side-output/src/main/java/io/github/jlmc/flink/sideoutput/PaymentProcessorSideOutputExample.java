package io.github.jlmc.flink.sideoutput;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.io.Serializable;

/// This example simulates a Payment Processor that validates transactions.
///
/// Valid transactions stay in the Main Stream, while invalid ones (negative amounts or zero) are diverted to a Dead Letter Queue (DLQ) side output.
public class PaymentProcessorSideOutputExample {

    // 1. Define the OutputTag for the Side Output.
    // We use a String tag to provide detailed error messages for the DLQ.
    public static final OutputTag<String> DLQ_TAG = new OutputTag<String>("dead-letter-queue"){};

    public static void main(String[] args) throws Exception {
        String brokers = System.getProperty("brokers", "localhost:9092");
        String inputTopic = System.getProperty("input-topic", "transaction");
        String successTopic = System.getProperty("success-topic", "transactions-success");
        String errorTopic = System.getProperty("error-topic", "transactions-errors");

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. Kafka Source for input
        KafkaSource<Transaction> source = KafkaSource.<Transaction>builder()
                .setBootstrapServers(brokers)
                .setTopics(inputTopic)
                .setGroupId("payment-processor-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new TransactionDeserializer())
                .build();

        DataStream<Transaction> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        // 3. Main Processing and Routing Logic
        SingleOutputStreamOperator<String> mainStream = defineWorkflowFromTransactions(input);

        // 4. Configure Kafka Sink for SUCCESS (Topic: transactions-success)
        KafkaSink<String> successSink = KafkaSink.<String>builder()
                .setBootstrapServers(brokers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(successTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        // 5. Configure Kafka Sink for ERRORS/DLQ (Topic: transactions-errors)
        KafkaSink<String> errorSink = KafkaSink.<String>builder()
                .setBootstrapServers(brokers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(errorTopic)
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

            private transient org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper mapper;

            @Override
            public void open(OpenContext openContext) throws Exception {
                super.open(openContext);
                this.mapper = new org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper();
            }

            @Override
            public void processElement(String value, Context ctx, Collector<String> out) {
                try {
                    double amount;
                    if (value.trim().startsWith("{")) {
                        Transaction transaction = mapper.readValue(value, Transaction.class);
                        if (transaction == null || transaction.amount() == null) {
                             throw new NumberFormatException("Could not find amount in JSON");
                        }
                        amount = transaction.amount();
                    } else {
                        amount = Double.parseDouble(value);
                    }

                    processAmount(amount, value, ctx, out);
                } catch (Exception e) {
                    // TECHNICAL ERROR: Parsing failure goes to Side Output
                    ctx.output(DLQ_TAG, "TECHNICAL_EXCEPTION: Non-numeric data (" + value + ")");
                }
            }
        });
    }

    public static SingleOutputStreamOperator<String> defineWorkflowFromTransactions(DataStream<Transaction> input) {
        return input.process(new ProcessFunction<Transaction, String>() {
            @Override
            public void processElement(Transaction value, Context ctx, Collector<String> out) {
                if (value == null || value.amount() == null) {
                    ctx.output(DLQ_TAG, "TECHNICAL_EXCEPTION: Null transaction or amount");
                    return;
                }
                processAmount(value.amount(), "{\"amount\": " + value.amount() + "}", ctx, out);
            }
        });
    }

    public static class TransactionDeserializer extends JsonDeserializationSchema<Transaction> {
        public TransactionDeserializer() {
            super(Transaction.class);
        }

        @Override
        public Transaction deserialize(byte[] message) {
            try {
                return super.deserialize(message);
            } catch (Exception e) {
                // Return a special "corrupted" transaction instead of failing
                return new Transaction(null);
            }
        }
    }

    private static void processAmount(double amount, String originalValue, ProcessFunction<?, String>.Context ctx, Collector<String> out) {
        if (amount > 0) {
            // SUCCESS: Forward to main flow (positive amounts)
            out.collect("VALID_TRANSACTION: " + amount);
        } else {
            // BUSINESS ERROR: Negative or zero values go to Side Output
            ctx.output(DLQ_TAG, "BUSINESS_EXCEPTION: Invalid amount (" + originalValue + ")");
        }
    }

    public record Transaction(@JsonProperty("amount") Double amount) implements Serializable {
        @JsonCreator
        public Transaction(@JsonProperty("amount") Double amount) {
            this.amount = amount;
        }

        @Override
        public String toString() {
            return "{\"amount\": " + amount + "}";
        }
    }
}
