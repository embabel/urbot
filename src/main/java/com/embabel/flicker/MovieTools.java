package com.embabel.flicker;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.UnfoldingTools;
import com.embabel.flicker.persistence.DrivineMovieRepository;
import com.embabel.flicker.persistence.WatchedMovieNode;
import com.embabel.flicker.service.MovieLookupService;
import com.embabel.flicker.service.OmdbClient;
import com.embabel.urbot.user.UrbotUserService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@UnfoldingTools(
        name = "movies",
        description = """
                Tools for looking up movies, checking streaming availability, and tracking watched movies
                """
)
@Service
public class MovieTools {

    private static final Logger logger = LoggerFactory.getLogger(MovieTools.class);

    private final MovieLookupService movieLookupService;
    private final @Nullable OmdbClient omdbClient;
    private final DrivineMovieRepository movieRepository;
    private final UrbotUserService userService;

    public MovieTools(
            MovieLookupService movieLookupService,
            @Nullable OmdbClient omdbClient,
            DrivineMovieRepository movieRepository,
            UrbotUserService userService) {
        this.movieLookupService = movieLookupService;
        this.omdbClient = omdbClient;
        this.movieRepository = movieRepository;
        this.userService = userService;
    }

    private String currentContext() {
        return userService.getAuthenticatedUser().effectiveContext();
    }

    @LlmTool(description = """
            Look up a movie by title. Returns detailed information including director, actors,
            plot, IMDB rating, and IMDB ID. Use this when the user asks about a specific movie.""")
    public String lookupMovie(String title) {
        return movieLookupService.lookupMovie(title);
    }

    @LlmTool(description = """
            Check where a movie is available to stream. Requires the IMDB ID (e.g. 'tt0043014')
            and a two-letter country code (e.g. 'us'). Use lookupMovie first to get the IMDB ID.""")
    public String checkStreaming(String imdbId, String countryCode) {
        return movieLookupService.checkStreaming(imdbId, countryCode);
    }

    @LlmTool(description = """
            Mark a movie as watched with a rating from 1 to 10. This saves it to the user's
            watched list. Look up the movie first to get full details.""")
    public String markAsWatched(String title, int rating) {
        if (omdbClient == null) {
            return "Cannot mark as watched — movie lookup is not available (OMDB API key not set).";
        }
        OmdbClient.MovieResponse movie = omdbClient.getMovieByTitle(title);
        if (movie == null) {
            return "Could not find a movie called '" + title + "'. Check the title and try again.";
        }

        String context = currentContext();

        // Check if already watched
        WatchedMovieNode existing = movieRepository.findByImdbId(context, movie.imdbId());
        if (existing != null) {
            existing.setRating(rating);
            movieRepository.save(existing);
            return "Updated rating for '%s' to %d/10.".formatted(movie.title(), rating);
        }

        int clampedRating = Math.max(1, Math.min(10, rating));
        WatchedMovieNode node = new WatchedMovieNode(
                context, movie.imdbId(), movie.title(), movie.director(), movie.genre(), clampedRating
        );
        movieRepository.save(node);
        return "Marked '%s' (%s, dir. %s) as watched with a rating of %d/10.".formatted(
                movie.title(), movie.year(), movie.director(), clampedRating);
    }

    @LlmTool(description = """
            Get the list of movies the user has watched, with their ratings.
            Returns titles, directors, genres, and ratings.""")
    public String getWatchedMovies() {
        String context = currentContext();
        var watched = movieRepository.findByContext(context);
        if (watched.isEmpty()) {
            return "No watched movies recorded yet.";
        }
        return watched.stream()
                .map(m -> "%s (dir. %s, %s) — %d/10".formatted(
                        m.getTitle(), m.getDirector(), m.getGenre(), m.getRating()))
                .collect(Collectors.joining("\n"));
    }

    @LlmTool(description = """
            Remove a movie from the user's watched list. Look up the movie first to get
            the correct title, then remove it.""")
    public String removeWatched(String title) {
        if (omdbClient == null) {
            return "Cannot remove — movie lookup is not available (OMDB API key not set).";
        }
        OmdbClient.MovieResponse movie = omdbClient.getMovieByTitle(title);
        if (movie == null) {
            return "Could not find a movie called '" + title + "'.";
        }

        String context = currentContext();
        WatchedMovieNode existing = movieRepository.findByImdbId(context, movie.imdbId());
        if (existing == null) {
            return "'" + movie.title() + "' is not in your watched list.";
        }

        movieRepository.deleteByImdbId(context, movie.imdbId());
        return "Removed '%s' from your watched list.".formatted(movie.title());
    }
}
