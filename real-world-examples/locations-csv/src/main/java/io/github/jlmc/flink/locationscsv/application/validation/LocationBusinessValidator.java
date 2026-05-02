package io.github.jlmc.flink.locationscsv.application.validation;

import io.github.jlmc.flink.locationscsv.domain.entity.Location;
import io.github.jlmc.flink.locationscsv.domain.entity.ValidationError;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LocationBusinessValidator extends ProcessFunction<Location, Location> {

    public static final OutputTag<ValidationError> ERROR_TAG = new OutputTag<ValidationError>("validation-errors") {};
    private transient List<ValidatorRule<Location>> rules;
    private transient long recordCounter;

    @Override
    public void processElement(Location location,
                               ProcessFunction<Location, Location>.Context context,
                               Collector<Location> out) {
        recordCounter++;
        List<ValidatorRule.Violation> allViolations = new ArrayList<>();

        // delegation to the validation rules...
        for (ValidatorRule<Location> rule : rules) {
            List<ValidatorRule.Violation> violations = rule.validate(location);
            allViolations.addAll(violations);
        }

        if (allViolations.isEmpty()) {
            out.collect(location);
        } else {
            emitError(context, location, allViolations);
        }
    }

    @Override
    public void open(OpenContext openContext) throws Exception {
        super.open(openContext);
        
        rules = List.of(
                new GeoRangeValidator(),
                new ImageAccessibilityValidator()
        );
    }

    private void emitError(ProcessFunction<Location, Location>.Context context, Location loc, List<ValidatorRule.Violation> violations) {
        String raw = String.format("%s (%s,%s)", loc.name(), loc.lat(), loc.lon());
        String reason = violations.stream()
                .map(ValidatorRule.Violation::message)
                .collect(Collectors.joining("; "));

        context.output(
                ERROR_TAG,
                new ValidationError(recordCounter, raw, reason, System.currentTimeMillis())
        );
    }
}
