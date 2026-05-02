package io.github.jlmc.flink.locationscsv.application.validation;

import io.github.jlmc.flink.locationscsv.domain.entity.ValidationError;
import io.github.jlmc.flink.locationscsv.source.S3ObjectCsvReaderFlatMap;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LocationWithSourceBusinessValidator extends ProcessFunction<S3ObjectCsvReaderFlatMap.LocationWithSource, S3ObjectCsvReaderFlatMap.LocationWithSource> {

    public static final OutputTag<ValidationError> ERROR_TAG = new OutputTag<ValidationError>("validation-errors") {
    };

    private transient List<ValidatorRule<io.github.jlmc.flink.locationscsv.domain.entity.Location>> rules;
    private transient long recordCounter;

    @Override
    public void processElement(S3ObjectCsvReaderFlatMap.LocationWithSource row,
                               ProcessFunction<S3ObjectCsvReaderFlatMap.LocationWithSource, S3ObjectCsvReaderFlatMap.LocationWithSource>.Context context,
                               Collector<S3ObjectCsvReaderFlatMap.LocationWithSource> out) {
        recordCounter++;

        List<ValidatorRule.Violation> allViolations = new ArrayList<>();
        for (ValidatorRule<io.github.jlmc.flink.locationscsv.domain.entity.Location> rule : rules) {
            allViolations.addAll(rule.validate(row.location()));
        }

        if (allViolations.isEmpty()) {
            out.collect(row);
        } else {
            String raw = String.format("%s (%s,%s)", row.location().name(), row.location().lat(), row.location().lon());
            String reason = allViolations.stream()
                    .map(ValidatorRule.Violation::message)
                    .collect(Collectors.joining("; "));

            context.output(
                    ERROR_TAG,
                    new ValidationError(recordCounter, raw, reason, System.currentTimeMillis())
            );
        }
    }

    @Override
    public void open(OpenContext openContext) throws Exception {
        super.open(openContext);

        rules = List.of(
                new GeoRangeValidator(),
                new ImageUrlValidator(),
                new UrlImageAccessibleValidator()
        );
    }
}
