package io.github.jlmc.flink.locationscsv.application.processing;

import io.github.jlmc.flink.locationscsv.domain.entity.FileProcessingError;
import io.github.jlmc.flink.locationscsv.domain.entity.FileProcessingMetric;
import io.github.jlmc.flink.locationscsv.domain.entity.FileProcessingResult;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.List;

public class FileProcessingResultAggregator extends KeyedProcessFunction<String, FileProcessingMetric, FileProcessingResult> {

    private transient ValueState<Long> validCountState;
    private transient ValueState<Long> invalidCountState;
    private transient ValueState<Boolean> completedState;
    private transient ListState<FileProcessingError> errorsState;

    @Override
    public void open(org.apache.flink.api.common.functions.OpenContext openContext) throws Exception {
        super.open(openContext);
        validCountState = getRuntimeContext().getState(new ValueStateDescriptor<>("valid-count", Long.class));
        invalidCountState = getRuntimeContext().getState(new ValueStateDescriptor<>("invalid-count", Long.class));
        completedState = getRuntimeContext().getState(new ValueStateDescriptor<>("completed", Boolean.class));
        errorsState = getRuntimeContext().getListState(new ListStateDescriptor<>("errors", FileProcessingError.class));
    }

    @Override
    public void processElement(FileProcessingMetric metric,
                               KeyedProcessFunction<String, FileProcessingMetric, FileProcessingResult>.Context context,
                               Collector<FileProcessingResult> out) throws Exception {
        switch (metric.metricType()) {
            case VALID_ROW -> validCountState.update(current(validCountState) + 1);
            case INVALID_ROW -> {
                invalidCountState.update(current(invalidCountState) + 1);
                errorsState.add(new FileProcessingError(metric.line(), metric.error()));
            }
            case FILE_COMPLETED -> {
                completedState.update(true);
                context.timerService().registerProcessingTimeTimer(context.timerService().currentProcessingTime() + 500);
            }
        }
    }

    @Override
    public void onTimer(long timestamp,
                        KeyedProcessFunction<String, FileProcessingMetric, FileProcessingResult>.OnTimerContext ctx,
                        Collector<FileProcessingResult> out) throws Exception {
        if (!Boolean.TRUE.equals(completedState.value())) {
            return;
        }

        long validCount = current(validCountState);
        long invalidCount = current(invalidCountState);
        List<FileProcessingError> errors = new ArrayList<>();
        for (FileProcessingError fileProcessingError : errorsState.get()) {
            errors.add(fileProcessingError);
        }

        String result;
        String message;
        if (invalidCount == 0) {
            result = "success";
            message = "success upload and processing all the locations";
        } else if (validCount > 0) {
            result = "partial_success";
            message = "some of the lines have errors";
        } else {
            result = "fail";
            message = "all the lines are invalid";
        }

        out.collect(new FileProcessingResult(ctx.getCurrentKey(), result, message, errors));

        validCountState.clear();
        invalidCountState.clear();
        completedState.clear();
        errorsState.clear();
    }

    private static long current(ValueState<Long> state) throws Exception {
        Long value = state.value();
        return value == null ? 0L : value;
    }
}
