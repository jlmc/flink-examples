package io.github.jlmc.flink.multistream;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoMapFunction;

public class CurrencyConverterCoMapFunction {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // stream1: sells (item, price in US)
        DataStream<SaleInUSD> salesDs = env.socketTextStream("localhost", 9999)
                .map(it -> {
                    var parts = it.split(":");
                    return new SaleInUSD(
                            parts[0],
                            Double.parseDouble(parts[1])
                    );
                });

        // stream2: exchange rates (value 1 USD in EUR)
        DataStream<Double> rateDs = env.socketTextStream("localhost", 9998)
                .map(Double::parseDouble);


        defineWorkflow(salesDs, rateDs)
                .print();

        env.execute("Currency Converter with CoMapFunction");
    }

    public static DataStream<SaleInEUR> defineWorkflow(DataStream<SaleInUSD> salesDs, DataStream<Double> rateDs) {
        return salesDs.connect(rateDs.broadcast())
                .map(new CoMapFunction<SaleInUSD, Double, SaleInEUR>() {

                    private Double currentRate = 1.0; // Default to 1.0 until we receive an exchange rate


                    @Override
                    public SaleInEUR map1(SaleInUSD value) throws Exception {
                        return new SaleInEUR(
                                value.item,
                                value.price * currentRate
                        );
                    }

                    @Override
                    public SaleInEUR map2(Double value) throws Exception {
                        currentRate = value; // Update the exchange rate

                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull);
    }

    public record  SaleInUSD(String item, Double price) {}
    public record  SaleInEUR(String item, Double price) {}
}
