package com.embabel.bot.astrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Geocodes place names to coordinates using the OpenStreetMap Nominatim API.
 * Free, no API key required.
 */
@Service
public class GeocodingService {

    private static final Logger logger = LoggerFactory.getLogger(GeocodingService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeocodingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "urbot/1.0")
                .build();
    }

    public record Coordinates(double latitude, double longitude, String displayName) {
    }

    /**
     * Resolve a place name to latitude/longitude.
     *
     * @param placeName e.g. "Lagos, Nigeria" or "Portland, Oregon"
     * @return coordinates, or null if not found
     */
    @Nullable
    public Coordinates geocode(String placeName) {
        logger.info("Geocoding: {}", placeName);
        String response = restClient.get()
                .uri("/search?q={q}&format=json&limit=1", placeName)
                .retrieve()
                .body(String.class);

        try {
            var results = objectMapper.readTree(response);
            if (results.isArray() && !results.isEmpty()) {
                JsonNode first = results.get(0);
                double lat = first.get("lat").asDouble();
                double lon = first.get("lon").asDouble();
                String displayName = first.get("display_name").asText();
                logger.info("Geocoded '{}' → {}, {} ({})", placeName, lat, lon, displayName);
                return new Coordinates(lat, lon, displayName);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse geocoding response for '{}'", placeName, e);
        }

        logger.warn("Could not geocode '{}'", placeName);
        return null;
    }
}
