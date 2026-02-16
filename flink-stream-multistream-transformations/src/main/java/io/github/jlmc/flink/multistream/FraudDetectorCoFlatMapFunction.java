package io.github.jlmc.flink.multistream;

import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.RichCoFlatMapFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;

public class FraudDetectorCoFlatMapFunction {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<Transaction> transactionStream = env.fromElements(
                new Transaction("user_A", 50.0),
                new Transaction("user_B", 1000.0),
                new Transaction("user_A", 5000.0)
        );

        DataStream<Rule> ruleStream = env.fromElements(
                new Rule("user_A", 200.0),
                new Rule("user_B", 500.0)
        );

        defineWorkflow(transactionStream, ruleStream)
                .print();

        env.execute("Fraud Detector with CoFlatMapFunction");
    }

    public static SingleOutputStreamOperator<Alert> defineWorkflow(DataStream<Transaction> transactionStream, DataStream<Rule> ruleStream) {
        return transactionStream
                .connect(ruleStream)
                .keyBy(Transaction::userId, Rule::userId) // Fluent method references with records
                .flatMap(new FraudDetector());
    }


    // 1. DATA MODELS (Using Java Records)
    // Records are immutable and serializable by default
    public record Transaction(String userId, double amount) implements Serializable {
    }

    public record Rule(String userId, double limit) implements Serializable {
    }

    public record Alert(String message, Transaction transaction) implements Serializable {
        @Override
        public String toString() {
            return ">>> ALERT: " + message + " | Data: " + transaction;
        }
    }

    // CoFlatMapFunction
    static class FraudDetector extends RichCoFlatMapFunction<Transaction, Rule, Alert> {

        private transient ValueState<Double> limitState;

        @Override
        public void open(OpenContext openContext) {
            ValueStateDescriptor<Double> descriptor = new ValueStateDescriptor<>("user-limit", Double.class);
            limitState = getRuntimeContext().getState(descriptor);
        }

        @Override
        public void flatMap1(Transaction transaction, Collector<Alert> out) throws Exception {
            Double currentLimit = limitState.value();

            if (currentLimit != null && transaction.amount() > currentLimit) {
                out.collect(new Alert("TRANSACTION_EXCEEDS_LIMIT", transaction));
            }

            System.out.printf("Processed Transaction: %s | Current Limit: %.2f%n", transaction, currentLimit != null ? currentLimit : 0.0);
        }

        @Override
        public void flatMap2(Rule rule, Collector<Alert> out) throws Exception {
            System.out.println("Updating rule for " + rule.userId() + " to $" + rule.limit());
            limitState.update(rule.limit());
        }
    }
}
