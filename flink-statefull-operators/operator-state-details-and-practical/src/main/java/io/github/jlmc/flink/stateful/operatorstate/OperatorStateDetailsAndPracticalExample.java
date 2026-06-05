package io.github.jlmc.flink.stateful.operatorstate;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Example demonstrating Operator State (non-keyed state) in Apache Flink.
 * It shows both Even-split redistribution and Union redistribution schemes.
 */
public class OperatorStateDetailsAndPracticalExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // --- 1. CONFIGURATION ---
        // Enable checkpointing to see state in action. 
        // In Flink, state is only persisted and redistributable if checkpointing is enabled.
        env.enableCheckpointing(10000);
        // Standard parallelism for the environment.
        env.setParallelism(2);

        DataStream<String> source = env.addSource(new SimpleSource())
                                       .name("SimpleSource")
                                       .uid("simple-source-id"); // Best Practice: Always set UIDs for stateful operators

        // --- 2. EVEN-SPLIT REDISTRIBUTION ---
        // Useful when you want to distribute load evenly across subtasks after rescaling.
        source.map(new EvenSplitStateMapper())
              .name("EvenSplitMapper")
              .uid("even-split-mapper-id")
              .setParallelism(4) 
              .addSink(new PrintingSink("EvenSplit"))
              .name("EvenSplitSink")
              .setParallelism(2);

        // --- 3. UNION REDISTRIBUTION ---
        // Useful for special cases where every subtask needs the full history (e.g., small metadata).
        source.map(new UnionStateMapper())
              .name("UnionStateMapper")
              .uid("union-state-mapper-id")
              .setParallelism(4)
              .addSink(new PrintingSink("Union"))
              .name("UnionSink")
              .setParallelism(2);

        env.execute("Operator State Example");
    }

    /**
     * A simple source that emits elements.
     */
    public static class SimpleSource implements SourceFunction<String> {
        private volatile boolean running = true;
        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            int count = 0;
            while (running && count < 20) {
                ctx.collect("element-" + count);
                count++;
                Thread.sleep(100);
            }
        }
        @Override
        public void cancel() {
            running = false;
        }
    }

    /**
     * Implements CheckpointedFunction to use Operator State with Even-split redistribution.
     */
    public static class EvenSplitStateMapper implements MapFunction<String, String>, CheckpointedFunction {
        // Local variable to store elements in memory during processing
        private List<String> bufferedElements;
        // Flink's managed state handle
        private transient ListState<String> checkpointedState;

        public EvenSplitStateMapper() {
            this.bufferedElements = new ArrayList<>();
        }

        @Override
        public String map(String value) throws Exception {
            bufferedElements.add(value);
            return "EvenSplit-Processed: " + value + " (Buffer size: " + bufferedElements.size() + ")";
        }

        /**
         * Called by Flink during a checkpoint. 
         * We must synchronize our local 'bufferedElements' into the 'checkpointedState'.
         */
        @Override
        public void snapshotState(FunctionSnapshotContext context) throws Exception {
            checkpointedState.clear();
            for (String element : bufferedElements) {
                checkpointedState.add(element);
            }
        }

        /**
         * Called when the operator is initialized (started for the first time or restored).
         */
        @Override
        public void initializeState(FunctionInitializationContext context) throws Exception {
            ListStateDescriptor<String> descriptor =
                    new ListStateDescriptor<>("even-split-buffered-elements", Types.STRING);

            // getListState uses Even-split redistribution by default.
            // On restore, Flink divides the total list among the new parallel subtasks.
            checkpointedState = context.getOperatorStateStore().getListState(descriptor);

            if (context.isRestored()) {
                // Populate the local buffer from the restored state
                for (String element : checkpointedState.get()) {
                    bufferedElements.add(element);
                }
                System.out.println("[DEBUG] Subtask " + context.getRestoredCheckpointId() + " Restored EvenSplit state: " + bufferedElements);
            }
        }
    }

    /**
     * Implements CheckpointedFunction to use Operator State with Union redistribution.
     */
    public static class UnionStateMapper implements MapFunction<String, String>, CheckpointedFunction {
        private List<String> bufferedElements;
        private transient ListState<String> checkpointedState;

        public UnionStateMapper() {
            this.bufferedElements = new ArrayList<>();
        }

        @Override
        public String map(String value) throws Exception {
            bufferedElements.add(value);
            return "Union-Processed: " + value + " (Buffer size: " + bufferedElements.size() + ")";
        }

        @Override
        public void snapshotState(FunctionSnapshotContext context) throws Exception {
            checkpointedState.clear();
            for (String element : bufferedElements) {
                checkpointedState.add(element);
            }
        }

        @Override
        public void initializeState(FunctionInitializationContext context) throws Exception {
            ListStateDescriptor<String> descriptor =
                    new ListStateDescriptor<>("union-buffered-elements", Types.STRING);

            // getUnionListState uses Union redistribution.
            // On restore, EVERY subtask gets the ENTIRE concatenated list from all previous subtasks.
            checkpointedState = context.getOperatorStateStore().getUnionListState(descriptor);

            if (context.isRestored()) {
                for (String element : checkpointedState.get()) {
                    bufferedElements.add(element);
                }
                System.out.println("[DEBUG] Restored Union state (ALL subtasks see ALL elements): " + bufferedElements);
            }
        }
    }

    public static class PrintingSink implements SinkFunction<String> {
        private final String prefix;
        public PrintingSink(String prefix) { this.prefix = prefix; }
        @Override
        public void invoke(String value, Context context) {
            System.out.println(prefix + " > " + value);
        }
    }
}
