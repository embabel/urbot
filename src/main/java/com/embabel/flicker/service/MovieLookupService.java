package com.embabel.flicker.service;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Combines OMDB and Streaming Availability lookups into formatted strings for LLM consumption.
 * Both API clients are optional — if the API key is missing, the client won't be injected.
 */
@Service
public class MovieLookupService {

    private static final Logger logger = LoggerFactory.getLogger(MovieLookupService.class);

    private final @Nullable OmdbClient omdbClient;
    private final @Nullable StreamingAvailabilityClient streamingClient;

    public MovieLookupService(
            @Nullable OmdbClient omdbClient,
            @Nullable StreamingAvailabilityClient streamingClient) {
        this.omdbClient = omdbClient;
        this.streamingClient = streamingClient;
        if (omdbClient == null) {
            logger.info("OMDB client not available — set OMDB_API_KEY to enable movie lookups");
        }
        if (streamingClient == null) {
            logger.info("Streaming client not available — set X_RAPIDAPI_KEY to enable streaming lookups");
        }
    }

    public String lookupMovie(String title) {
        if (omdbClient == null) {
            return "Movie lookup is not available — OMDB API key is not configured.";
        }
        OmdbClient.MovieResponse movie = omdbClient.getMovieByTitle(title);
        if (movie == null) {
            return "Could not find a movie called '" + title + "'.";
        }
        return formatMovie(movie);
    }

    public String checkStreaming(String imdbId, String countryCode) {
        if (streamingClient == null) {
            return "Streaming lookup is not available — RapidAPI key is not configured.";
        }
        List<StreamingAvailabilityClient.StreamingOption> options =
                streamingClient.getStreamingOptions(imdbId, countryCode);
        if (options.isEmpty()) {
            return "No streaming options found for " + imdbId + " in " + countryCode + ".";
        }
        return options.stream()
                .map(o -> o.service().name() + " (" + o.type() + "): " + o.link())
                .collect(Collectors.joining("\n"));
    }

    private String formatMovie(OmdbClient.MovieResponse movie) {
        return """
                Title: %s
                Year: %s
                Director: %s
                Actors: %s
                Genre: %s
                Plot: %s
                IMDB Rating: %s
                IMDB ID: %s
                Runtime: %s
                Rated: %s""".formatted(
                movie.title(), movie.year(), movie.director(), movie.actors(),
                movie.genre(), movie.plot(), movie.imdbRating(), movie.imdbId(),
                movie.runtime(), movie.rated());
    }
}
