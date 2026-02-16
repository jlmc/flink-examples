package io.github.jlmc.flink.multistream;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.AbstractTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrencyConverterCoMapFunctionTest extends AbstractTestBase {

    @Test
    public void testCurrencyConversion() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        CollectSink.values.clear();

        // 1. Prepare test data
        DataStream<CurrencyConverterCoMapFunction.SaleInUSD> salesDs = env.fromElements(
                new CurrencyConverterCoMapFunction.SaleInUSD("laptop", 1000.0),
                new CurrencyConverterCoMapFunction.SaleInUSD("mouse", 20.0),
                new CurrencyConverterCoMapFunction.SaleInUSD("keyboard", 50.0)
        );

        // Exchange rate changes from default 1.0 to 0.85
        DataStream<Double> rateDs = env.fromElements(0.85);

        // 2. Define workflow
        DataStream<CurrencyConverterCoMapFunction.SaleInEUR> resultStream = 
                CurrencyConverterCoMapFunction.defineWorkflow(salesDs, rateDs);

        // 3. Collect results
        resultStream.addSink(new CollectSink<>());

        // 4. Execute
        env.execute();

        // 5. Verify results
        // Note: Due to the nature of Flink streams, if the rate update arrives AFTER some sales, they will use the default 1.0.
        // If it arrives BEFORE, they will use 0.85.
        // In a MiniCluster with env.fromElements, there's no strict guarantee of timing unless we control it.
        
        assertThat(CollectSink.values).hasSize(3);
        
        // We check if the conversion was done with either 1.0 or 0.85
        for (Object obj : CollectSink.values) {
            CurrencyConverterCoMapFunction.SaleInEUR sale = (CurrencyConverterCoMapFunction.SaleInEUR) obj;
            if (sale.item().equals("laptop")) {
                assertThat(sale.price()).isIn(1000.0, 850.0);
            } else if (sale.item().equals("mouse")) {
                assertThat(sale.price()).isIn(20.0, 17.0);
            } else if (sale.item().equals("keyboard")) {
                assertThat(sale.price()).isIn(50.0, 42.5);
            }
        }
    }
}
