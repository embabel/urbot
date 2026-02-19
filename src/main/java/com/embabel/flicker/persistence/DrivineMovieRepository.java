package com.embabel.flicker.persistence;

import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class DrivineMovieRepository {

    private static final Logger logger = LoggerFactory.getLogger(DrivineMovieRepository.class);

    private final GraphObjectManager graphObjectManager;
    private final PersistenceManager persistenceManager;

    public DrivineMovieRepository(GraphObjectManager graphObjectManager, PersistenceManager persistenceManager) {
        this.graphObjectManager = graphObjectManager;
        this.persistenceManager = persistenceManager;
    }

    @Transactional
    public void save(WatchedMovieNode node) {
        graphObjectManager.save(node, CascadeType.NONE);
        logger.info("Saved watched movie: {} ({})", node.getTitle(), node.getImdbId());
    }

    @Transactional(readOnly = true)
    public List<WatchedMovieNode> findByContext(String contextId) {
        var cypher = """
                MATCH (m:WatchedMovie {contextId: $contextId})
                RETURN m.id AS id
                ORDER BY m.watchedAt DESC
                """;
        var ids = persistenceManager.query(
                QuerySpecification
                        .withStatement(cypher)
                        .bind(Map.of("contextId", contextId))
                        .transform(String.class)
        );
        return ids.stream()
                .map(id -> graphObjectManager.load(id, WatchedMovieNode.class))
                .filter(m -> m != null)
                .toList();
    }

    @Transactional(readOnly = true)
    @Nullable
    public WatchedMovieNode findByImdbId(String contextId, String imdbId) {
        var cypher = """
                MATCH (m:WatchedMovie {contextId: $contextId, imdbId: $imdbId})
                RETURN m.id AS id
                LIMIT 1
                """;
        var ids = persistenceManager.query(
                QuerySpecification
                        .withStatement(cypher)
                        .bind(Map.of("contextId", contextId, "imdbId", imdbId))
                        .transform(String.class)
        );
        if (ids.isEmpty()) {
            return null;
        }
        return graphObjectManager.load(ids.getFirst(), WatchedMovieNode.class);
    }

    @Transactional
    public void deleteByImdbId(String contextId, String imdbId) {
        var cypher = """
                MATCH (m:WatchedMovie {contextId: $contextId, imdbId: $imdbId})
                DETACH DELETE m
                """;
        persistenceManager.execute(
                QuerySpecification
                        .withStatement(cypher)
                        .bind(Map.of("contextId", contextId, "imdbId", imdbId))
        );
        logger.info("Deleted watched movie with imdbId {} from context {}", imdbId, contextId);
    }
}
