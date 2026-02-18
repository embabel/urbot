package com.embabel.bot.astrid.domain;

import com.embabel.agent.rag.model.Relationship;

import java.util.List;

public interface Person extends com.embabel.domain.common.Person {

    @Relationship(name = "HAS_VISITED")
    List<Place> hasVisited();

    @Relationship(name = "WANTS_TO_VISIT")
    List<Place> wantsToVisit();

    @Relationship(name = "LIVES_IN")
    Place livesIn();
}
