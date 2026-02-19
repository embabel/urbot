package com.embabel.flicker.persistence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;

import java.time.Instant;
import java.util.UUID;

@NodeFragment(labels = {"WatchedMovie"})
public class WatchedMovieNode {

    @NodeId
    private String id;

    private String contextId;
    private String imdbId;
    private String title;
    private String director;
    private String genre;
    private int rating;
    private Instant watchedAt;

    @JsonCreator
    public WatchedMovieNode(
            @JsonProperty("id") String id,
            @JsonProperty("contextId") String contextId,
            @JsonProperty("imdbId") String imdbId,
            @JsonProperty("title") String title,
            @JsonProperty("director") String director,
            @JsonProperty("genre") String genre,
            @JsonProperty("rating") int rating,
            @JsonProperty("watchedAt") Instant watchedAt) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.contextId = contextId != null ? contextId : "default";
        this.imdbId = imdbId;
        this.title = title;
        this.director = director;
        this.genre = genre;
        this.rating = rating;
        this.watchedAt = watchedAt != null ? watchedAt : Instant.now();
    }

    public WatchedMovieNode(String contextId, String imdbId, String title, String director, String genre, int rating) {
        this(UUID.randomUUID().toString(), contextId, imdbId, title, director, genre, rating, Instant.now());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }

    public String getImdbId() { return imdbId; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public Instant getWatchedAt() { return watchedAt; }
    public void setWatchedAt(Instant watchedAt) { this.watchedAt = watchedAt; }

    @Override
    public String toString() {
        return "WatchedMovieNode{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", rating=" + rating +
                '}';
    }
}
