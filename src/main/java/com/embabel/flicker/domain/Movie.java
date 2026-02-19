package com.embabel.flicker.domain;

import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public interface Movie extends NamedEntity {

    @JsonPropertyDescription("The genre of the movie, e.g. 'Film Noir', 'Drama'")
    String getGenre();

    @JsonPropertyDescription("The director of the movie")
    String getDirector();
}
