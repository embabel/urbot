package com.embabel.urbot.proposition.persistence;

import com.embabel.dice.proposition.EntityMention;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * A reference to an entity within a proposition.
 * Neo4j graph representation of EntityMention from the Dice project.
 */
@NodeFragment(labels = {"Mention"})
public class Mention {

    @NodeId
    private String id;

    private String span;
    private String type;
    private @Nullable String resolvedId;
    private MentionRole role;

    @JsonCreator
    public Mention(
            @JsonProperty("id") String id,
            @JsonProperty("span") String span,
            @JsonProperty("type") String type,
            @JsonProperty("resolvedId") @Nullable String resolvedId,
            @JsonProperty("role") MentionRole role) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.span = span;
        this.type = type;
        this.resolvedId = resolvedId;
        this.role = role != null ? role : MentionRole.OTHER;
    }

    public Mention(String span, String type, @Nullable String resolvedId, MentionRole role) {
        this(UUID.randomUUID().toString(), span, type, resolvedId, role);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSpan() { return span; }
    public void setSpan(String span) { this.span = span; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public @Nullable String getResolvedId() { return resolvedId; }
    public void setResolvedId(@Nullable String resolvedId) { this.resolvedId = resolvedId; }

    public MentionRole getRole() { return role; }
    public void setRole(MentionRole role) { this.role = role; }

    public static Mention fromDice(EntityMention em) {
        MentionRole role = switch (em.getRole()) {
            case SUBJECT -> MentionRole.SUBJECT;
            case OBJECT -> MentionRole.OBJECT;
            case OTHER -> MentionRole.OTHER;
        };
        return new Mention(em.getSpan(), em.getType(), em.getResolvedId(), role);
    }

    public EntityMention toDice() {
        com.embabel.dice.proposition.MentionRole diceRole = switch (role) {
            case SUBJECT -> com.embabel.dice.proposition.MentionRole.SUBJECT;
            case OBJECT -> com.embabel.dice.proposition.MentionRole.OBJECT;
            case OTHER -> com.embabel.dice.proposition.MentionRole.OTHER;
        };
        return new EntityMention(span, type, resolvedId, diceRole, Map.of());
    }

    @Override
    public String toString() {
        String resolved = resolvedId != null ? "\u2192" + resolvedId : "?";
        return span + ":" + type + resolved;
    }
}
