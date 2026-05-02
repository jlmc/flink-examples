package io.github.jlmc.flink.locationscsv.application.validation;

import io.github.jlmc.flink.locationscsv.domain.entity.Location;

import java.util.ArrayList;
import java.util.List;

public class GeoRangeValidator implements ValidatorRule<Location> {

    @Override
    public List<Violation> validate(Location location) {

        Double lat = location.lat();
        Double lon = location.lon();

        List<Violation> violations = new ArrayList<>();

        if (lat == null || lat < -90 || lat > 90) {
            violations.add(new Violation("Latitude out of geographic bounds (-90 to 90)."));
        }

        if (lon == null || lon < -180 || lon > 180) {
            violations.add(new Violation("Longitude out of geographic bounds (-180 to 180)."));
        }

        return List.copyOf(violations);
    }
}
