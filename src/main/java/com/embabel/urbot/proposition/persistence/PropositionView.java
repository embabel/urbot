package com.embabel.urbot.proposition.persistence;

import com.embabel.dice.proposition.Proposition;
import org.drivine.annotation.Direction;
import org.drivine.annotation.GraphRelationship;
import org.drivine.annotation.GraphView;
import org.drivine.annotation.Root;

import java.util.List;
import java.util.Map;

/**
 * GraphView combining a Proposition with its entity Mentions.
 */
@GraphView
public class PropositionView {

    @Root
    private PropositionNode proposition;

    @GraphRelationship(type = "HAS_MENTION", direction = Direction.OUTGOING)
    private List<Mention> mentions;

    public PropositionView() {
    }

    public PropositionView(PropositionNode proposition, List<Mention> mentions) {
        this.proposition = proposition;
        this.mentions = mentions;
    }

    public PropositionNode getProposition() { return proposition; }
    public void setProposition(PropositionNode proposition) { this.proposition = proposition; }

    public List<Mention> getMentions() { return mentions; }
    public void setMentions(List<Mention> mentions) { this.mentions = mentions; }

    public static PropositionView fromDice(Proposition p) {
        var propNode = new PropositionNode(
                p.getId(),
                p.getContextIdValue(),
                p.getText(),
                p.getConfidence(),
                p.getDecay(),
                p.getImportance(),
                p.getReasoning(),
                p.getGrounding(),
                p.getCreated(),
                p.getRevised(),
                p.getStatus(),
                p.getUri(),
                p.getSourceIds()
        );
        var mentionNodes = p.getMentions().stream()
                .map(Mention::fromDice)
                .toList();
        return new PropositionView(propNode, mentionNodes);
    }

    public Proposition toDice() {
        var diceMentions = mentions.stream()
                .map(Mention::toDice)
                .toList();
        return Proposition.create(
                proposition.getId(),
                proposition.getContextId(),
                proposition.getText(),
                diceMentions,
                proposition.getConfidence(),
                proposition.getDecay(),
                proposition.getImportance(),
                proposition.getReasoning(),
                proposition.getGrounding(),
                proposition.getCreated(),
                proposition.getRevised(),
                proposition.getStatus(),
                0,
                proposition.getSourceIds(),
                0,
                Map.of(),
                proposition.getUri()
        );
    }

    @Override
    public String toString() {
        return "PropositionView{" +
                "proposition=" + proposition +
                ", mentions=" + mentions +
                '}';
    }
}
