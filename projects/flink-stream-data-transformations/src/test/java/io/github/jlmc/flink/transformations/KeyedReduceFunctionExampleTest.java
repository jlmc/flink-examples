package io.github.jlmc.flink.transformations;

import io.github.jlmc.flink.transformations.KeyedReduceFunctionExample.Transaction;
import io.github.jlmc.flink.transformations.KeyedReduceFunctionExample.TransactionSource;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class KeyedReduceFunctionExampleTest {
    @RegisterExtension
    static final MiniClusterExtension FLINK_CLUSTER = new MiniClusterExtension(
            new MiniClusterResourceConfiguration.Builder()
                    .setNumberSlotsPerTaskManager(2)
                    .setNumberTaskManagers(1)
                    .build());

    @BeforeEach
    void setUp() {
        CollectSink.VALUES.clear();
    }

    @Test
    void testTransactionTotalReduce() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        Transaction[] testTransactions = getTestTransactions();
        DataStream<Transaction> transactionDataStream = KeyedReduceFunctionExample.buildTransactionStream(env, new FakeTransactionSource(testTransactions));

        SingleOutputStreamOperator<Transaction> reduce = KeyedReduceFunctionExample.getReduce(transactionDataStream);

        reduce.addSink(new CollectSink<>());

        env.execute("Keyed Reduce Function Test");

        assertThat(CollectSink.<Transaction>values())
                .contains(
                        new Transaction("Electronics", 360.0),
                        new Transaction("Apparel", 75.0),
                        new Transaction("Home", 200.0),
                        new Transaction("Books", 50.0)
                );
    }

    static class FakeTransactionSource implements TransactionSource {
        AtomicInteger counter = new AtomicInteger(0);

        final Transaction[] transactions;

        FakeTransactionSource(Transaction[] transactions) {
            this.transactions = transactions;
        }

        @Override
        public void run(SourceContext<Transaction> ctx) throws Exception {
            while (counter.get() < transactions.length) {
                int index = counter.getAndIncrement();
                Transaction transaction = transactions[index];
                ctx.collect(transaction);
                Thread.sleep(100);
            }
        }

        @Override
        public void cancel() {

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
