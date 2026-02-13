package com.embabel.urbot.proposition.persistence;

import org.drivine.mapper.RowMapper;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Maps Neo4j vector search results to proposition ID and score pairs.
 * Uses Drivine's RowMapper interface which properly handles Neo4j driver value types.
 */
class PropositionSimilarityMapper implements RowMapper<PropositionSimilarityResult> {

    @Override
    public @NonNull PropositionSimilarityResult map(@NonNull Map<String, ?> row) {
        var id = (String) row.get("id");
        var score = ((Number) row.get("score")).doubleValue();
        return new PropositionSimilarityResult(id, score);
    }
}
