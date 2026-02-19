package com.embabel.flicker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client for the OMDB API.
 * See <a href="https://www.omdbapi.com/">omdbapi.com</a>
 */
@Service
@ConditionalOnProperty("OMDB_API_KEY")
public class OmdbClient {

    private static final Logger logger = LoggerFactory.getLogger(OmdbClient.class);

    private final String apiKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OmdbClient(
            @Value("${OMDB_API_KEY:}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("http://omdbapi.com")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Nullable
    public MovieResponse getMovieByTitle(String title) {
        try {
            String rawResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("apikey", apiKey)
                            .queryParam("t", title)
                            .build())
                    .retrieve()
                    .body(String.class);

            try {
                return objectMapper.readValue(rawResponse, MovieResponse.class);
            } catch (Exception e) {
                logger.warn("Error parsing response for title: {}. Raw response: {}", title, rawResponse);
                return null;
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch movie by title: {}", title, e);
            return null;
        }
    }

    public MovieResponse getMovieById(String imdbId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("apikey", apiKey)
                        .queryParam("i", imdbId)
                        .build())
                .retrieve()
                .body(MovieResponse.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieResponse(
            @JsonProperty("Title") String title,
            @JsonProperty("Year") String year,
            @JsonProperty("Rated") String rated,
            @JsonProperty("Runtime") String runtime,
            @JsonProperty("Genre") String genre,
            @JsonProperty("Director") String director,
            @JsonProperty("Actors") String actors,
            @JsonProperty("Plot") String plot,
            @JsonProperty("imdbRating") String imdbRating,
            @JsonProperty("imdbID") String imdbId,
            @JsonProperty("Poster") String poster
    ) {
    }
}
