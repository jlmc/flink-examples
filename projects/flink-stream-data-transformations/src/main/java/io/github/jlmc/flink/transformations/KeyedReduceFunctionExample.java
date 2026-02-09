package io.github.jlmc.flink.transformations;

import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.io.Serializable;

public class KeyedReduceFunctionExample {

    public static void main(String[] args) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // This example is covered in KeyedStreamTransformationsExample.java

        RandonTransactionSource function = new RandonTransactionSource();

        DataStream<Transaction> transactions = buildTransactionStream(env, function);


        getReduce(transactions)
                .print();
    }

    static DataStream<Transaction> buildTransactionStream(StreamExecutionEnvironment env, TransactionSource source) {
        //RandonTransactionSource function = new RandonTransactionSource();
        return env.addSource(source);
    }

    static SingleOutputStreamOperator<Transaction> getReduce(DataStream<Transaction> transactions) {
        return transactions
                // 1. keyBy: Group the stream by the "category" field.
                // This ensures all "Electronics" sales go to the same operator task.
                .keyBy(transaction -> transaction.category())

                // 2. reduce: Update the total revenue for that category.
                // The reduce transformation combines the current state with the new event.
                .reduce(new ReduceFunction<Transaction>() {
                    @Override
                    public Transaction reduce(Transaction currentTotal, Transaction newTransaction) {
                        // Logic: Create a new Transaction object representing the sum.
                        // Note: The output type remains the same as the input type.
                        double updatedAmount = currentTotal.amount() + newTransaction.amount();

                        return new Transaction(
                                currentTotal.category(),
                                updatedAmount
                        );
                    }
                });
    }

    interface TransactionSource extends SourceFunction<Transaction> {
        String[] CATEGORIES = {"Food", "Electronics", "Clothing"};
    }

    public record Transaction(String category, double amount) implements Serializable {
    }

    static class RandonTransactionSource implements TransactionSource {
        private volatile boolean running = true;

        @Override
        public void run(SourceContext<Transaction> ctx) throws Exception {
            while (running) {
                String category = CATEGORIES[(int) (Math.random() * CATEGORIES.length)];
                double amount = Math.random() * 100;

                ctx.collect(new Transaction(category, amount));

                // Throttle the source to simulate realistic traffic (1 per second)
                Thread.sleep(1000);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    public static Transaction[] getTestTransactions() {
        return new Transaction[] {
                new Transaction("Electronics", 100.0),
                new Transaction("Apparel", 50.0),
                new Transaction("Electronics", 200.0), // Sum for Electronics should be 300.0
                new Transaction("Home", 150.0),
                new Transaction("Apparel", 25.0),     // Sum for Apparel should be 75.0
                new Transaction("Electronics", 50.0),  // Sum for Electronics should be 350.0
                new Transaction("Home", 50.0),       // Sum for Home should be 200.0
                new Transaction("Books", 30.0),
                new Transaction("Books", 20.0),      // Sum for Books should be 50.0
                new Transaction("Electronics", 10.0)   // Final sum for Electronics: 360.0
        };
    }
}
