package io.github.jlmc.flink.locationscsv.application.validation;

import io.github.jlmc.flink.locationscsv.domain.entity.Location;

import java.util.ArrayList;
import java.util.List;

public class ImageUrlValidator implements ValidatorRule<Location> {

    @Override
    public List<Violation> validate(Location location) {
        String imageUrl = location.imgUrl();
        List<Violation> violations = new ArrayList<>();

        if (imageUrl == null || imageUrl.isBlank()) {
            violations.add(new Violation("Image URL is null or blank."));
        }

        return List.copyOf(violations);
    }
}
