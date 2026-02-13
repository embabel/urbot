package com.embabel.urbot.proposition.persistence;

/**
 * The role an entity mention plays in a proposition.
 */
public enum MentionRole {
    /**
     * The subject of the statement (e.g., "Jim" in "Jim knows Neo4j")
     */
    SUBJECT,

    /**
     * The object of the statement (e.g., "Neo4j" in "Jim knows Neo4j")
     */
    OBJECT,

    /**
     * Other mention that doesn't fit subject/object pattern
     */
    OTHER
}
