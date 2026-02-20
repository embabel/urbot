package com.embabel.urbot.proposition.persistence;

import com.embabel.dice.proposition.PropositionStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A proposition is a natural language statement with typed entity mentions.
 * Neo4j graph representation of Proposition from the Dice project.
 * <p>
 * Propositions are the system of record - all other representations
 * (Neo4j relationships, vector embeddings) derive from them.
 */
@NodeFragment(labels = {"Proposition"})
public class PropositionNode {

    @NodeId
    private String id;

    private String contextId;
    private String text;
    private double confidence;
    private double decay;
    private double importance;
    private @Nullable String reasoning;
    private List<String> grounding;
    private Instant created;
    private Instant revised;
    private Instant lastAccessed;
    private PropositionStatus status;
    private @Nullable String uri;
    private List<String> sourceIds;
    private @Nullable List<Double> embedding;

    @JsonCreator
    public PropositionNode(
            @JsonProperty("id") String id,
            @JsonProperty("contextId") String contextId,
            @JsonProperty("text") String text,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("decay") double decay,
            @JsonProperty("importance") double importance,
            @JsonProperty("reasoning") @Nullable String reasoning,
            @JsonProperty("grounding") List<String> grounding,
            @JsonProperty("created") Instant created,
            @JsonProperty("revised") Instant revised,
            @JsonProperty("lastAccessed") @Nullable Instant lastAccessed,
            @JsonProperty("status") PropositionStatus status,
            @JsonProperty("uri") @Nullable String uri,
            @JsonProperty("sourceIds") List<String> sourceIds) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.contextId = contextId != null ? contextId : "default";
        this.text = text;
        this.confidence = confidence;
        this.decay = decay;
        this.importance = importance;
        this.reasoning = reasoning;
        this.grounding = grounding != null ? grounding : List.of();
        this.created = created != null ? created : Instant.now();
        this.revised = revised != null ? revised : Instant.now();
        this.lastAccessed = lastAccessed != null ? lastAccessed : Instant.now();
        this.status = status != null ? status : PropositionStatus.ACTIVE;
        this.uri = uri;
        this.sourceIds = sourceIds != null ? sourceIds : List.of();
    }

    public PropositionNode(String text, double confidence) {
        this(UUID.randomUUID().toString(), "default", text, confidence, 0.0, 0.5, null, List.of(),
                Instant.now(), Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public double getDecay() { return decay; }
    public void setDecay(double decay) { this.decay = decay; }

    public double getImportance() { return importance; }
    public void setImportance(double importance) { this.importance = importance; }

    public @Nullable String getReasoning() { return reasoning; }
    public void setReasoning(@Nullable String reasoning) { this.reasoning = reasoning; }

    public List<String> getGrounding() { return grounding; }
    public void setGrounding(List<String> grounding) { this.grounding = grounding; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant created) { this.created = created; }

    public Instant getRevised() { return revised; }
    public void setRevised(Instant revised) { this.revised = revised; }

    public Instant getLastAccessed() { return lastAccessed; }
    public void setLastAccessed(Instant lastAccessed) { this.lastAccessed = lastAccessed; }

    public PropositionStatus getStatus() { return status; }
    public void setStatus(PropositionStatus status) { this.status = status; }

    public @Nullable String getUri() { return uri; }
    public void setUri(@Nullable String uri) { this.uri = uri; }

    public List<String> getSourceIds() { return sourceIds; }
    public void setSourceIds(List<String> sourceIds) { this.sourceIds = sourceIds; }

    public @Nullable List<Double> getEmbedding() { return embedding; }
    public void setEmbedding(@Nullable List<Double> embedding) { this.embedding = embedding; }

    @Override
    public String toString() {
        return "PropositionNode{" +
                "id='" + id + '\'' +
                ", text='" + text + '\'' +
                ", confidence=" + confidence +
                ", status=" + status +
                '}';
    }
}
