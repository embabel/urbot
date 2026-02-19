package com.embabel.flicker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client for the Streaming Availability API on RapidAPI.
 * See <a href="https://docs.movieofthenight.com/">docs.movieofthenight.com</a>
 */
@Service
@ConditionalOnProperty("X_RAPIDAPI_KEY")
public class StreamingAvailabilityClient {

    private static final Logger logger = LoggerFactory.getLogger(StreamingAvailabilityClient.class);

    private final RestClient restClient;

    public StreamingAvailabilityClient(
            @Value("${X_RAPIDAPI_KEY:}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl("https://streaming-availability.p.rapidapi.com/")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-RapidAPI-Key", apiKey)
                .build();
    }

    public ShowResponse getShow(String imdbId) {
        return restClient.get()
                .uri("shows/{imdbId}", imdbId)
                .retrieve()
                .body(ShowResponse.class);
    }

    public List<StreamingOption> getStreamingOptions(String imdbId, String countryCode) {
        try {
            ShowResponse show = getShow(imdbId);
            if (show != null && show.streamingOptions() != null) {
                StreamingOption[] options = show.streamingOptions().get(countryCode);
                return options != null ? List.of(options) : Collections.emptyList();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            logger.warn("Failed to get streaming options for {}: {}", imdbId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamingOption(
            ServiceOption service,
            String type,
            String link,
            @Nullable String quality
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceOption(
            String id,
            String name,
            String homePage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShowResponse(
            Map<String, StreamingOption[]> streamingOptions
    ) {
    }
}
