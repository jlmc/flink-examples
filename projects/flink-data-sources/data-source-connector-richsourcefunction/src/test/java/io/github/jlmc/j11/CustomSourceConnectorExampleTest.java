package io.github.jlmc.j11;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.AbstractTestBase;
import org.apache.flink.util.CloseableIterator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomSourceConnectorExampleTest extends AbstractTestBase {

    @Test
    void simpleRichParallelSourceFunction() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<Long> stream = CustomSourceConnectorExample.simpleRichParallelSourceFunction(env);

        List<Long> result = new ArrayList<>();
        try (CloseableIterator<Long> iterator = stream.executeAndCollect()) {
            while (iterator.hasNext()) {
                result.add(iterator.next());
            }
        }

        // SimpleRichParallelSourceFunction(10, 100) produces numbers from 10 to 100 inclusive.
        // Total numbers: 100 - 10 + 1 = 91.
        
        assertFalse(result.isEmpty());
        assertTrue(result.size() >= 91, "Should have produced at least 91 elements, but got " + result.size());
        assertTrue(result.contains(10L));
        assertTrue(result.contains(100L));
    }

    @Test
    void simpleRichSourceFunction() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<Long> stream = CustomSourceConnectorExample.simpleRichSourceFunction(env);

        List<Long> result = new ArrayList<>();
        try (CloseableIterator<Long> iterator = stream.executeAndCollect()) {
            // It's an infinite stream, so we just take some elements and stop.
            int count = 0;
            while (iterator.hasNext() && count < 5) {
                result.add(iterator.next());
                count++;
            }
        }

        assertFalse(result.isEmpty());
        assertTrue(result.size() >= 1);
    }
}
