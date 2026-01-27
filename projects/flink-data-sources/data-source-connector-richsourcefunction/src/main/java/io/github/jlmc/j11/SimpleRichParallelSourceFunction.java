package io.github.jlmc.j11;

import org.apache.flink.api.common.TaskInfo;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleRichParallelSourceFunction extends RichParallelSourceFunction<Long> {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SimpleRichParallelSourceFunction.class);

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final long from;
    private final long end;

    public SimpleRichParallelSourceFunction(long from, long end) {
        this.from = from;
        this.end = end;
    }

    public SimpleRichParallelSourceFunction(long from) {
        this(from, Long.MAX_VALUE);
    }

    @Override
    public void open(OpenContext openContext) throws Exception {
        super.open(openContext);
    }

    @Override
    public void run(SourceContext<Long> ctx) throws Exception {
        LOGGER.info("Starting SimpleRichParallelSourceFunction...");
        TaskInfo taskInfo = getRuntimeContext().getTaskInfo();
        int total = taskInfo.getNumberOfParallelSubtasks();
        int current = taskInfo.getIndexOfThisSubtask();

        long totalNum = end - from + 1;
        long totalTasks = (totalNum / total) == 0 ? 1 : (totalNum / total);

        long start = from + (current * totalTasks);
        long stop = Math.min(start + totalTasks - 1, end);

        if (start >= this.end) return;

        long to = this.from + totalTasks * (current + 1);

        if (current == total - 1) {
            to = this.end;
        }

        for (long i = start; i <= to && running.get(); i++) {
            //synchronized (ctx.getCheckpointLock()) {
            ctx.collect(i);
            //TimeUnit.SECONDS.sleep(1L);
            //}
        }


    }

    @Override
    public void cancel() {
        LOGGER.info("Canceling SimpleRichParallelSourceFunction...");
        running.set(false);
    }
}
