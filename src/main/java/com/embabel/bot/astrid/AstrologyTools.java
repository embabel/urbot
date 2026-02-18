package com.embabel.bot.astrid;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.UnfoldingTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Astrology API tool provider for Embabel agents.
 * <p>
 * Wraps the ryuphi/astrology-api (Swiss Ephemeris) running in Docker.
 * API endpoint: GET /horoscope?time={ISO8601}&latitude={lat}&longitude={lng}&houseSystem={code}
 * <p>
 * Start with: docker compose --profile astrology up -d
 */
@UnfoldingTools(
        name = "astrology",
        description = """
                Tools relating to horoscopes and astrology
                """
)
@Service
public class AstrologyTools {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AstrologyTools(
            @Value("${astrology.api.base-url:http://localhost:3000}") String baseUrl,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @LlmTool(description = """
            Calculate a natal chart (birth chart) for a person given their birth date/time and location.
            Returns planetary positions, house cusps, and aspects.
            Use Placidus house system by default unless the user specifies otherwise.
            The time must be in ISO 8601 format (e.g., '1990-07-04T15:30:00Z' for UTC,
            or '1990-07-04T15:30:00+12:00' for NZST).
            Latitude is positive for North, negative for South.
            Longitude is positive for East, negative for West.""")
    public String calculateNatalChart(
            String dateTimeIso,
            double latitude,
            double longitude,
            String houseSystem) {

        if (houseSystem == null || houseSystem.isBlank()) {
            houseSystem = "P";
        }

        String response = restClient.get()
                .uri("/horoscope?time={time}&latitude={lat}&longitude={lng}&houseSystem={hs}",
                        dateTimeIso, latitude, longitude, houseSystem)
                .retrieve()
                .body(String.class);

        if (response == null) {
            return "Error: No response from astrology API";
        }

        return formatChartResponse(response);
    }

    @LlmTool(description = """
            Look up which zodiac sign (sun sign) a person is based on their birth date.
            This is a simple lookup that doesn't require exact birth time or location.
            The dateTimeIso should be the birth date at noon UTC, e.g., '1990-07-04T12:00:00Z'.
            Uses latitude 0 and longitude 0 since only the Sun's zodiac position matters for sun sign.""")
    public String getSunSign(String dateTimeIso) {
        String response = restClient.get()
                .uri("/horoscope?time={time}&latitude=0&longitude=0&houseSystem=W",
                        dateTimeIso)
                .retrieve()
                .body(String.class);

        if (response == null) {
            return "Error: No response from astrology API";
        }

        return extractSunSign(response);
    }

    @LlmTool(description = """
            Get the available house systems for astrological chart calculation.
            Returns a list of house system codes and their names.
            Common ones: P=Placidus, K=Koch, W=Whole Sign, R=Regiomontanus, C=Campanus, O=Porphyry.""")
    public String getHouseSystems() {
        return """
                Available house systems:
                P - Placidus (most common in Western astrology)
                K - Koch
                W - Whole Sign (popular in Hellenistic astrology)
                R - Regiomontanus
                C - Campanus
                O - Porphyry
                B - Alcabitius
                M - Morinus
                A - Equal
                E - Equal (variant)
                D - Equal (MC)
                T - Polich/Page (Topocentric)
                H - Horizon/Azimuth""";
    }

    private String formatChartResponse(String json) {
        try {
            JsonNode chart = objectMapper.readTree(json);
            StringBuilder sb = new StringBuilder();

            // Format planets
            JsonNode planets = chart.get("planets");
            if (planets != null && planets.isArray()) {
                sb.append("=== Planetary Positions ===\n");
                for (JsonNode planet : planets) {
                    String name = getTextOrDefault(planet, "name", "Unknown");
                    String sign = getTextOrDefault(planet, "sign", "?");
                    double degree = getDoubleOrDefault(planet, "normDegree", 0.0);
                    int house = getIntOrDefault(planet, "house", 0);
                    boolean retro = getBoolOrDefault(planet, "isRetrograde", false);
                    String retroMark = retro ? " (R)" : "";
                    sb.append("  %s: %s %s, House %d%s%n".formatted(
                            name, formatDegree(degree), sign, house, retroMark));
                }
            }

            // Format houses
            JsonNode houses = chart.get("houses");
            if (houses != null && houses.isArray()) {
                sb.append("\n=== House Cusps ===\n");
                AtomicInteger houseNum = new AtomicInteger(1);
                for (JsonNode house : houses) {
                    String sign = getTextOrDefault(house, "sign", "?");
                    double degree = getDoubleOrDefault(house, "degree", 0.0);
                    sb.append("  House %d: %s %s%n".formatted(
                            houseNum.getAndIncrement(), formatDegree(degree), sign));
                }
            }

            // Format aspects
            JsonNode aspects = chart.get("aspects");
            if (aspects != null && aspects.isArray()) {
                sb.append("\n=== Aspects ===\n");
                for (JsonNode aspect : aspects) {
                    String p1 = getTextOrDefault(aspect, "point1", "?");
                    String p2 = getTextOrDefault(aspect, "point2", "?");
                    String type = getTextOrDefault(aspect, "type", "?");
                    double orb = getDoubleOrDefault(aspect, "orb", 0.0);
                    sb.append("  %s %s %s (orb: %.1f°)%n".formatted(p1, type, p2, orb));
                }
            }

            // If the response structure is different, just return pretty-printed JSON
            if (sb.isEmpty()) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(chart);
            }
            return sb.toString();

        } catch (Exception e) {
            // Fallback: return the raw JSON if parsing fails — the LLM can handle it
            return json;
        }
    }

    private String extractSunSign(String json) {
        try {
            JsonNode chart = objectMapper.readTree(json);
            JsonNode planets = chart.get("planets");

            if (planets != null && planets.isArray()) {
                for (JsonNode planet : planets) {
                    if ("Sun".equals(planet.path("name").asText())) {
                        String sign = getTextOrDefault(planet, "sign", "Unknown");
                        double degree = getDoubleOrDefault(planet, "normDegree", 0.0);
                        return "Sun is in %s at %s".formatted(sign, formatDegree(degree));
                    }
                }
            }

            return json;

        } catch (Exception e) {
            return json;
        }
    }

    private String formatDegree(double degree) {
        int deg = (int) degree;
        int min = (int) ((degree - deg) * 60);
        return "%d°%d'".formatted(deg, min);
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value != null ? value.asText() : defaultValue;
    }

    private double getDoubleOrDefault(JsonNode node, String field, double defaultValue) {
        JsonNode value = node.get(field);
        return value != null ? value.asDouble() : defaultValue;
    }

    private int getIntOrDefault(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        return value != null ? value.asInt() : defaultValue;
    }

    private boolean getBoolOrDefault(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node.get(field);
        return value != null ? value.asBoolean() : defaultValue;
    }
}
