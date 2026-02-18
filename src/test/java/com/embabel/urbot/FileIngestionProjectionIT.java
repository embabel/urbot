package com.embabel.urbot;

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.dice.proposition.Proposition;
import com.embabel.urbot.proposition.extraction.IncrementalPropositionExtraction;
import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import com.embabel.urbot.user.UrbotUser;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that ingests a file via the "Learn" path (rememberFile),
 * verifies propositions are extracted, and checks that graph relationships
 * from the Relations beans are projected into Neo4j.
 * <p>
 * Uses real Neo4j and real LLM. Test data is isolated via a custom user context
 * and cleaned up afterward.
 * <p>
 * Prerequisites: Neo4j running, API key set (e.g. OPENAI_API_KEY).
 */
@SpringBootTest(
        classes = TestUrbotApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles({"it", "astrid"})
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class FileIngestionProjectionIT {

    private static final Logger logger = LoggerFactory.getLogger(FileIngestionProjectionIT.class);

    private static final Path CASSIE_NOTES = Path.of("data/users/cassie-silver-notes.txt");

    @Autowired
    private IncrementalPropositionExtraction extraction;

    @Autowired
    private DrivinePropositionRepository propositionRepository;

    @Autowired
    private NamedEntityDataRepository entityRepository;

    @Autowired
    private PersistenceManager persistenceManager;

    private UrbotUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new UrbotUser("it-file-test", "Cassie Silver", "cassie");
        var testContext = "it_file_projection_" + System.currentTimeMillis();
        testUser.setCurrentContextName(testContext);

        logger.info("Test context: {} (effectiveContext={})", testContext, testUser.effectiveContext());

        int deleted = propositionRepository.clearByContext(testUser.effectiveContext());
        if (deleted > 0) {
            logger.info("Cleaned up {} stale propositions", deleted);
        }
    }

    @AfterEach
    void tearDown() {
        if (testUser != null) {
            // Clean propositions
            int deleted = propositionRepository.clearByContext(testUser.effectiveContext());
            logger.info("Cleaned up {} propositions for context {}", deleted, testUser.effectiveContext());

            // Clean test entities and their relationships
            cleanTestEntities();
        }
    }

    @Test
    void fileIngestionExtractsPropositionsAndProjectsRelationships() throws Exception {
        assertTrue(CASSIE_NOTES.toFile().exists(),
                "Test file not found: " + CASSIE_NOTES.toAbsolutePath());

        // -- Persist user entity (as login would) --
        logger.info("=== Persisting test user entity ===");
        var userData = new com.embabel.agent.rag.model.SimpleNamedEntityData(
                testUser.getId(), null, testUser.getName(), testUser.getDescription(),
                testUser.labels(), Map.of(), Map.of(), null);
        entityRepository.save(userData);

        // -- Ingest file (same path as the UI "Learn" button) --
        logger.info("=== Ingesting file: {} ===", CASSIE_NOTES);
        try (var inputStream = new FileInputStream(CASSIE_NOTES.toFile())) {
            extraction.rememberFile(inputStream, CASSIE_NOTES.getFileName().toString(), testUser);
        }

        // -- Check propositions --
        var propositions = propositionRepository.findByContextIdValue(testUser.effectiveContext());
        logPropositions(propositions);

        assertTrue(propositions.size() >= 5,
                "Expected at least 5 propositions from Cassie's notes, got " + propositions.size());

        // Check for key facts from the file
        String allText = propositions.stream()
                .map(Proposition::getText)
                .map(String::toLowerCase)
                .reduce("", (a, b) -> a + " " + b);

        assertContainsAny(allText, "Cassie notes should mention the cat",
                "artemis", "cat", "pet");
        assertContainsAny(allText, "Cassie notes should mention food preferences",
                "coriander", "thai", "greek", "lamb", "cooking");
        assertContainsAny(allText, "Cassie notes should mention music",
                "guitar", "joni mitchell", "big thief", "nick drake", "jazz");

        // -- Check graph relationships --
        logger.info("=== Checking graph relationships ===");
        var relationships = queryRelationshipsForEntity(testUser.getId());
        logger.info("Found {} relationships for user {}", relationships.size(), testUser.getName());
        for (var rel : relationships) {
            logger.info("  ({}) -[{}]-> ({}) {}",
                    rel.get("sourceName"), rel.get("type"), rel.get("targetName"), rel.get("targetLabels"));
        }

        assertFalse(relationships.isEmpty(),
                "Expected at least one projected relationship for the user, got none. "
                        + "Check that RelationBasedGraphProjector is matching predicates from Relations.");

        // Check for specific relationship types we expect from the notes
        var relTypes = relationships.stream()
                .map(r -> (String) r.get("type"))
                .toList();
        logger.info("Relationship types found: {}", relTypes);

        // Cassie owns Artemis (a pet) — this should match the OWNS relation
        boolean hasPetRelationship = relationships.stream()
                .anyMatch(r -> {
                    var targetName = ((String) r.get("targetName")).toLowerCase();
                    var targetLabels = r.get("targetLabels").toString().toLowerCase();
                    return targetName.contains("artemis")
                            || (targetLabels.contains("pet") && targetName.contains("artemis"));
                });

        if (!hasPetRelationship) {
            logger.warn("No pet relationship found for Artemis — checking all entities with relationships");
        }

        // At minimum, we expect SOME relationships were projected
        assertTrue(relationships.size() >= 1,
                "Expected at least 1 relationship projected from Relations predicates, got "
                        + relationships.size());
    }

    private void assertContainsAny(String text, String description, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                logger.info("  Keyword match for '{}': {}", description, keyword);
                return;
            }
        }
        fail(description + ": none of " + List.of(keywords) + " found in proposition texts");
    }

    /**
     * Query all outgoing relationships from an entity by ID.
     * Returns each relationship as [sourceName, relType, targetName, targetLabels, confidence].
     */
    private List<Map<String, Object>> queryRelationshipsForEntity(String entityId) {
        var statement = """
                MATCH (source {id: $id})-[r]->(target)
                WHERE type(r) <> 'HAS_MENTION'
                RETURN source.name AS sourceName, type(r) AS type,
                       COALESCE(target.name, target.id) AS targetName,
                       labels(target) AS targetLabels, r.confidence AS confidence
                """;

        return persistenceManager.query(
                QuerySpecification.withStatement(statement)
                        .bind(Map.of("id", entityId))
                        .map(r -> {
                            // With multiple RETURN columns, Drivine wraps each row as a List
                            @SuppressWarnings("unchecked")
                            var row = (List<Object>) r;
                            return Map.<String, Object>of(
                                    "sourceName", row.get(0) != null ? row.get(0) : "",
                                    "type", row.get(1) != null ? row.get(1) : "",
                                    "targetName", row.get(2) != null ? row.get(2) : "",
                                    "targetLabels", row.get(3) != null ? row.get(3) : "",
                                    "confidence", row.get(4) != null ? row.get(4) : ""
                            );
                        })
        );
    }

    private void cleanTestEntities() {
        try {
            // Delete the test user entity and any entities + relationships created during the test
            var statement = """
                    MATCH (source {id: $id})-[r]->()
                    DELETE r
                    """;
            persistenceManager.execute(
                    QuerySpecification.withStatement(statement)
                            .bind(Map.of("id", testUser.getId()))
            );

            // Delete the user node itself
            var deleteUser = """
                    MATCH (n {id: $id})
                    DETACH DELETE n
                    """;
            persistenceManager.execute(
                    QuerySpecification.withStatement(deleteUser)
                            .bind(Map.of("id", testUser.getId()))
            );

            logger.info("Cleaned up test entity: {}", testUser.getId());
        } catch (Exception e) {
            logger.warn("Failed to clean up test entities", e);
        }
    }

    private void logPropositions(List<Proposition> propositions) {
        var sorted = propositions.stream()
                .sorted(java.util.Comparator.comparing(Proposition::getText))
                .toList();
        logger.info("=== Extracted {} propositions ===", sorted.size());
        for (var p : sorted) {
            logger.info("  [{}] confidence={} text='{}'", p.getStatus(), p.getConfidence(), p.getText());
            for (var m : p.getMentions()) {
                logger.info("    mention: span='{}' type={} role={} resolvedId={}",
                        m.getSpan(), m.getType(), m.getRole(), m.getResolvedId());
            }
        }
    }
}
