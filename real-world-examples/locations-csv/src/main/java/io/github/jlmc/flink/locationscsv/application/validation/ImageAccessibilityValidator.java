package io.github.jlmc.flink.locationscsv.application.validation;

import io.github.jlmc.flink.locationscsv.domain.entity.Location;

import java.util.ArrayList;
import java.util.List;

public class ImageAccessibilityValidator implements ValidatorRule<Location> {

    @Override
    public List<Violation> validate(Location location) {
        String imageUrl = location.imgUrl();
        List<Violation> violations = new ArrayList<>();

        if (imageUrl == null || imageUrl.isBlank()) {
            violations.add(new Violation("Image URL is null or blank."));
        } else if (!isImageAccessible(imageUrl)) {
            violations.add(new Violation("Image URL is not accessible: " + imageUrl));
        }

        return List.copyOf(violations);
    }

    private boolean isImageAccessible(String imageUrl) {
        // Implement logic to verify whether the image URL is accessible
        // It can be an HTTP HEAD request to verify URL status
        // Return true if the image is accessible, false otherwise
        return true; // Simplified placeholder
    }
}
