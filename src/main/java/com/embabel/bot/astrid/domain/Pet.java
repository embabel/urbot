package com.embabel.bot.astrid.domain;

import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public interface Pet extends NamedEntity {

    @JsonPropertyDescription("Type of pet, all lower case e.g., \"dog\", \"cat\", \"parrot\"")
    String getType();
}
