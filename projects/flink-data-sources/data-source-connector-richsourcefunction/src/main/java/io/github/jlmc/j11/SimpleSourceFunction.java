package io.github.jlmc.j11;

import org.apache.flink.api.common.TaskInfo;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.slf4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleSourceFunction extends RichSourceFunction<Long> {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SimpleSourceFunction.class);

    private static final AtomicBoolean running = new AtomicBoolean(true);

    @Override
    public void open(OpenContext openContext) {
        TaskInfo taskInfo = getRuntimeContext().getTaskInfo();
        int subtasks = taskInfo.getNumberOfParallelSubtasks();
        int indexSubtask = taskInfo.getIndexOfThisSubtask();

        LOGGER.info("Starting SimpleSourceFunction with {} subtasks. This is subtask #{}.", subtasks, indexSubtask);
    }

    @Override
    public void run(SourceContext<Long> ctx) throws Exception {
        while (running.get()) {
            long value = ThreadLocalRandom.current().nextLong(2_000);
            ctx.collect(value);
            TimeUnit.MILLISECONDS.sleep(value);
        }
    }

    @Override
    public void cancel() {
        running.set(false);
    }
}
