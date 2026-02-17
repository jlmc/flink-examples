package io.github.jlmc.flink.sideoutput;

import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple sink that collects elements into a static list.
 * Note: Since this is used in a MiniCluster, the static list works as long as
 * the tests run in the same JVM.
 */
public class CollectSink<T> implements SinkFunction<T> {
    public static final List<Object> VALUES = Collections.synchronizedList(new ArrayList<>());

    @SuppressWarnings("unchecked")
    public static <T> List<T> values() {
        return (List<T>) VALUES;
    }

    @Override
    public void invoke(T value, Context context) {
        VALUES.add(value);
    }
}
