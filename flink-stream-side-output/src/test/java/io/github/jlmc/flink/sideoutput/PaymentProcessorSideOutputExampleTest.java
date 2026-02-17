package io.github.jlmc.flink.sideoutput;

import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.AbstractTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentProcessorSideOutputExampleTest extends AbstractTestBase {

    @BeforeEach
    void setUp() {
        CollectSink.VALUES.clear();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Should route valid transactions to the main stream")
    void shouldRouteValidTransactionsToMainStream() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        var input = env.fromData("100.50", "200.0");

        SingleOutputStreamOperator<String> mainStream = PaymentProcessorSideOutputExample.defineWorkflow(input);
        mainStream.addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.VALUES).containsExactlyInAnyOrder(
                "VALID_TRANSACTION: 100.5",
                "VALID_TRANSACTION: 200.0"
        );
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Should route invalid amounts to the side output")
    void shouldRouteInvalidAmountsToSideOutput() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        var input = env.fromData("-5.0", "0.0");

        SingleOutputStreamOperator<String> mainStream = PaymentProcessorSideOutputExample.defineWorkflow(input);
        mainStream.getSideOutput(PaymentProcessorSideOutputExample.DLQ_TAG).addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.VALUES).containsExactlyInAnyOrder(
                "BUSINESS_EXCEPTION: Invalid amount (-5.0)",
                "BUSINESS_EXCEPTION: Invalid amount (0.0)"
        );
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Should route non-numeric data to the side output")
    void shouldRouteNonNumericDataToSideOutput() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        var input = env.fromData("corrupted_json");

        SingleOutputStreamOperator<String> mainStream = PaymentProcessorSideOutputExample.defineWorkflow(input);
        mainStream.getSideOutput(PaymentProcessorSideOutputExample.DLQ_TAG).addSink(new CollectSink<>());

        env.execute();

        assertThat(CollectSink.VALUES).containsExactlyInAnyOrder(
                "TECHNICAL_EXCEPTION: Non-numeric data (corrupted_json)"
        );
    }
}
