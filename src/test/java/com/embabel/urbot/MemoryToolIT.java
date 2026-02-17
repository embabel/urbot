package com.embabel.urbot;

import com.embabel.agent.api.common.ExecutingOperationContext;
import com.embabel.dice.agent.Memory;
import com.embabel.dice.projection.memory.MemoryProjector;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated test for memory tool usage.
 * Seeds propositions directly, then asks a question via PromptRunner with the memory tool.
 * No chatbot, no extraction pipeline — just LLM + memory tool.
 */
@SpringBootTest(
        classes = TestUrbotApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("it")
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class MemoryToolIT {

    private static final Logger logger = LoggerFactory.getLogger(MemoryToolIT.class);

    private static final String CONTEXT_ID = "memory-tool-test";

    @Autowired
    private DrivinePropositionRepository propositionRepository;

    @Autowired
    private MemoryProjector memoryProjector;

    @Autowired
    private ExecutingOperationContext eop;

    @Autowired
    private UrbotProperties properties;

    @BeforeEach
    void setUp() {
        propositionRepository.clearByContext(CONTEXT_ID);

        // Seed propositions directly — no extraction pipeline
        var propositions = List.of(
                prop("Claudia Carter plays guitar since age 12"),
                prop("Claudia Carter works at Meridian Labs as a software engineer"),
                prop("Claudia Carter loves sushi"),
                prop("Claudia Carter has a rescue dog named Miso"),
                prop("Claudia Carter is training for a half marathon in October"),
                prop("Claudia Carter visited Japan last year and loved Kyoto"),
                prop("Claudia Carter roasts her own coffee beans at home"),
                prop("Claudia Carter is learning Rust for a side project"),
                prop("Claudia Carter is in a covers band called The Resets"),
                prop("Claudia Carter does bouldering at a gym called Crux")
        );

        for (var p : propositions) {
            propositionRepository.save(p);
        }

        var stored = propositionRepository.findByContextIdValue(CONTEXT_ID);
        logger.info("Seeded {} propositions in context {}", stored.size(), CONTEXT_ID);
        assertEquals(propositions.size(), stored.size(), "All propositions should be stored");
    }

    @AfterEach
    void tearDown() {
        int deleted = propositionRepository.clearByContext(CONTEXT_ID);
        logger.info("Cleaned up {} propositions", deleted);
    }

    @Test
    @DisplayName("LLM uses memory tool to answer a question about hobbies")
    void memoryToolUsedForHobbies() {
        var memory = Memory.forContext(CONTEXT_ID)
                .withRepository(propositionRepository)
                .withProjector(memoryProjector);

        var result = eop.ai()
                .withLlm(properties.chat().llm())
                .withTools(memory)
                .withSystemPrompt(
                        "You are a personal assistant. " +
                        "You MUST use the memory tool to look up information about the user before answering. " +
                        "NEVER answer from general knowledge — always search memory first."
                )
                .generateText("What are Claudia's hobbies?");

        logger.info("Response: {}", result);

        // Should have found hobbies from memory
        var lower = result.toLowerCase();
        assertTrue(
                lower.contains("guitar") || lower.contains("bouldering") || lower.contains("climbing"),
                "Response should mention at least one hobby from memory. Got: " + result
        );
    }

    @Test
    @DisplayName("LLM uses memory tool for open-ended question")
    void memoryToolUsedForSummary() {
        var memory = Memory.forContext(CONTEXT_ID)
                .withRepository(propositionRepository)
                .withProjector(memoryProjector);

        var result = eop.ai()
                .withLlm(properties.chat().llm())
                .withTools(memory)
                .withSystemPrompt(
                        "You are a personal assistant. " +
                        "You MUST use the memory tool to look up information about the user before answering. " +
                        "NEVER answer from general knowledge — always search memory first. " +
                        "For open-ended questions, make multiple searches with different keywords."
                )
                .generateText("Tell me everything you know about Claudia Carter.");

        logger.info("Response: {}", result);

        // Count how many facts appear
        var lower = result.toLowerCase();
        int factCount = 0;
        if (lower.contains("guitar")) factCount++;
        if (lower.contains("meridian") || lower.contains("software engineer")) factCount++;
        if (lower.contains("sushi")) factCount++;
        if (lower.contains("miso") || lower.contains("dog")) factCount++;
        if (lower.contains("marathon") || lower.contains("running")) factCount++;
        if (lower.contains("japan") || lower.contains("kyoto")) factCount++;
        if (lower.contains("coffee")) factCount++;
        if (lower.contains("rust")) factCount++;
        if (lower.contains("resets") || lower.contains("band")) factCount++;
        if (lower.contains("bouldering") || lower.contains("climbing") || lower.contains("crux")) factCount++;

        logger.info("Facts found in response: {}/10", factCount);

        assertTrue(factCount >= 5,
                "Response should mention at least 5 of 10 seeded facts. Found " + factCount + ". Got: " + result);
    }

    private Proposition prop(String text) {
        var now = Instant.now();
        return Proposition.create(
                UUID.randomUUID().toString(),
                CONTEXT_ID,
                text,
                List.of(),
                0.95,
                0.1,
                null,
                List.of(),
                now,
                now,
                PropositionStatus.ACTIVE
        );
    }
}
