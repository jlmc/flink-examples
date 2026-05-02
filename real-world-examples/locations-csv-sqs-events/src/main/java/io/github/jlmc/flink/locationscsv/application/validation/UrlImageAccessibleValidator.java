package io.github.jlmc.flink.locationscsv.application.validation;

import io.github.jlmc.flink.locationscsv.domain.entity.Location;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class UrlImageAccessibleValidator implements ValidatorRule<Location> {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean isImageAccessible(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))

                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<Violation> validate(Location location) {
        List<Violation> violations = new ArrayList<>();

        if (location.imgUrl() != null && !location.imgUrl().isBlank()) {
            boolean imageAccessible = isImageAccessible(location.imgUrl());
            if (!imageAccessible) {
                violations.add(new Violation("Image URL '%s' is not public or can not be accessed.".formatted(location.imgUrl())));
            }
        }

        return List.copyOf(violations);
    }
}
