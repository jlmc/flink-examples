package io.github.jlmc.flink.locationscsv.application.validation;

import java.util.List;

public interface ValidatorRule<T> {

    List<Violation> validate(T location);

    record Violation(String message) {}
}
