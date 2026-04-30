package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * Shared process-window function to keep examples concise.
 */
public class ExampleWindowPrinter extends ProcessWindowFunction<WatermarkExamplesModels.SensorEvent, String, String, TimeWindow> {

    @Override
    public void process(String key,
                        ProcessWindowFunction<WatermarkExamplesModels.SensorEvent, String, String, TimeWindow>.Context context,
                        Iterable<WatermarkExamplesModels.SensorEvent> elements,
                        Collector<String> out) {
        long count = elements.spliterator().estimateSize();
        TimeWindow w = context.window();
        out.collect("key=" + key +
                ", window=[" + DateFormatUtils.format(w.getStart(), "HH:mm:ss.SSS") +
                " - " + DateFormatUtils.format(w.getEnd(), "HH:mm:ss.SSS") +
                "], count=" + count);
    }
}
