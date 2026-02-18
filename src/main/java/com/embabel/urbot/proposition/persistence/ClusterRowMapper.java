package com.embabel.urbot.proposition.persistence;

import org.drivine.mapper.RowMapper;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

/**
 * Maps Neo4j cluster query results (single-column map with anchorId + similar list)
 * to anchorId → similarity results entries.
 */
class ClusterRowMapper implements RowMapper<Map.Entry<String, List<PropositionSimilarityResult>>> {

    @Override
    @SuppressWarnings("unchecked")
    public Map.@NonNull Entry<String, List<PropositionSimilarityResult>> map(@NonNull Map<String, ?> row) {
        var anchorId = (String) row.get("anchorId");
        var similarRaw = (List<Map<String, Object>>) row.get("similar");
        var results = similarRaw.stream()
                .map(m -> new PropositionSimilarityResult(
                        (String) m.get("id"),
                        ((Number) m.get("score")).doubleValue()
                ))
                .toList();
        return Map.entry(anchorId, results);
    }
}
