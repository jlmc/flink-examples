package io.github.jlmc.flink.multistream;

import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectSink<T> implements SinkFunction<T> {
    public static final List<Object> values = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void invoke(T value, Context context) {
        values.add(value);
    }
}
