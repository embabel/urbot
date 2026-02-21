package com.embabel.urbot;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.dice.common.ConversationAnalysisRequestEvent;
import com.embabel.dice.proposition.extraction.IncrementalPropositionExtraction;
import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import com.embabel.urbot.user.UrbotUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that proves the core value chain works end-to-end:
 * user sends chat messages -> LLM responds -> proposition extraction runs -> memory is persisted to Neo4j.
 * <p>
 * Uses real Neo4j and real LLM (no mocks). Test data is isolated via a custom user context
 * and cleaned up afterward.
 * <p>
 * Prerequisites: Neo4j running, API key set (e.g. OPENAI_API_KEY).
 */
@SpringBootTest(
        classes = TestUrbotApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("it")
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class MemoryExtractionIT {

    private static final Logger logger = LoggerFactory.getLogger(MemoryExtractionIT.class);

    @Autowired
    private Chatbot chatbot;

    @Autowired
    private IncrementalPropositionExtraction propositionExtraction;

    @Autowired
    private DrivinePropositionRepository propositionRepository;

    private UrbotUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new UrbotUser("it-test", "Claudia Carter", "ccarter");
        var testContext = "it_test_memory_" + System.currentTimeMillis();
        testUser.setCurrentContextName(testContext);

        logger.info("Test context: {} (effectiveContext={})", testContext, testUser.effectiveContext());

        // Clean up any stale data for this context
        int deleted = propositionRepository.clearByContext(testUser.effectiveContext());
        if (deleted > 0) {
            logger.info("Cleaned up {} stale propositions", deleted);
        }
    }

    @AfterEach
    void tearDown() {
        if (testUser != null) {
            int deleted = propositionRepository.clearByContext(testUser.effectiveContext());
            logger.info("Cleaned up {} propositions for context {}", deleted, testUser.effectiveContext());
        }
    }

    @Test
    void chatConversationExtractsPropositionsToNeo4j() throws Exception {
        var script = loadScript("memory-extraction-script.txt");

        // -- Drive conversation --
        BlockingQueue<Message> responseQueue = new ArrayBlockingQueue<>(10);
        OutputChannel outputChannel = new CollectingOutputChannel(responseQueue);
        var chatSession = chatbot.createSession(testUser, outputChannel, null, null);

        for (String text : script.messages()) {
            logger.info("Sending: {}", text);
            chatSession.onUserMessage(new UserMessage(text));

            Message response = responseQueue.poll(120, TimeUnit.SECONDS);
            assertNotNull(response, "Expected a response for message: " + text);
            logger.info("Response: {}", truncate(response.getContent(), 200));
        }

        // -- First extraction --
        logger.info("=== Extraction 1 ===");
        var event = new ConversationAnalysisRequestEvent(
                this, testUser, chatSession.getConversation());
        propositionExtraction.extractPropositions(event);

        var propositions = propositionRepository.findByContextIdValue(testUser.effectiveContext());
        assertPropositions(propositions, script);
        int firstRunCount = propositions.size();
        logger.info("Extraction 1 complete: {} propositions", firstRunCount);

        // -- Second extraction on the same conversation: should not duplicate --
        logger.info("=== Extraction 2 (deduplication check) ===");
        propositionExtraction.extractPropositions(event);

        var propositionsAfterSecondRun = propositionRepository
                .findByContextIdValue(testUser.effectiveContext());
        logger.info("Extraction 2 complete: {} propositions (was {})",
                propositionsAfterSecondRun.size(), firstRunCount);

        assertTrue(propositionsAfterSecondRun.size() <= firstRunCount,
                "Second extraction on the same conversation should not create duplicates. Was "
                        + firstRunCount + ", now " + propositionsAfterSecondRun.size());

        // Write final propositions to file before cleanup deletes them
        writePropositions(propositionsAfterSecondRun);
    }

    private void assertPropositions(List<Proposition> propositions, Script script) {
        logger.info("Found {} propositions for context {}", propositions.size(), testUser.effectiveContext());
        for (Proposition p : propositions) {
            logger.info("  [{}] confidence={} text='{}'", p.getStatus(), p.getConfidence(), p.getText());
        }

        assertTrue(propositions.size() >= 2,
                "Expected at least 2 propositions, got " + propositions.size());

        for (Proposition p : propositions) {
            assertEquals(testUser.effectiveContext(), p.getContextIdValue(),
                    "Proposition contextId mismatch");
            assertEquals(PropositionStatus.ACTIVE, p.getStatus(),
                    "Expected ACTIVE status for proposition: " + p.getText());
            assertTrue(p.getConfidence() > 0,
                    "Expected positive confidence for proposition: " + p.getText());
        }

        String allText = propositions.stream()
                .map(Proposition::getText)
                .map(t -> t.toLowerCase(Locale.ROOT))
                .reduce("", (a, b) -> a + " " + b);

        long matchCount = 0;
        for (String keyword : script.keywords()) {
            if (allText.contains(keyword)) {
                logger.info("  Keyword match: {}", keyword);
                matchCount++;
            }
        }

        assertTrue(matchCount >= 2,
                "Expected at least 2 keyword matches in proposition texts, got " + matchCount
                        + ". All text: " + allText);

        logger.info("Assertions passed: {} propositions with {} keyword matches", propositions.size(), matchCount);
    }

    private static void writePropositions(List<Proposition> propositions) throws IOException {
        var outputDir = Path.of("target", "it-results");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("propositions-" + Instant.now().toEpochMilli() + ".txt");

        var sb = new StringBuilder();
        sb.append("# Extracted propositions (").append(propositions.size()).append(")\n");
        sb.append("# Generated: ").append(Instant.now()).append("\n\n");
        for (Proposition p : propositions) {
            sb.append(String.format("[%s] confidence=%.2f  %s%n", p.getStatus(), p.getConfidence(), p.getText()));
        }

        Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
        logger.info("Wrote propositions to {}", outputFile);
    }

    private record Script(List<String> messages, List<String> keywords) {}

    private static Script loadScript(String resourceName) throws IOException {
        try (var in = MemoryExtractionIT.class.getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(in, "Script not found on classpath: " + resourceName);
            var lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();

            var messages = lines.stream()
                    .filter(l -> !l.isBlank() && !l.startsWith("#") && !l.startsWith("keywords:"))
                    .toList();
            var keywords = lines.stream()
                    .filter(l -> l.startsWith("keywords:"))
                    .findFirst()
                    .map(l -> Arrays.stream(l.substring("keywords:".length()).split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList())
                    .orElse(List.of());

            assertFalse(messages.isEmpty(), "Script has no messages");
            assertFalse(keywords.isEmpty(), "Script has no keywords");
            return new Script(messages, keywords);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * Simple output channel that collects assistant messages into a blocking queue,
     * mirroring the pattern used by {@code ChatView.VaadinOutputChannel}.
     */
    private static class CollectingOutputChannel implements OutputChannel {

        private final BlockingQueue<Message> queue;

        CollectingOutputChannel(BlockingQueue<Message> queue) {
            this.queue = queue;
        }

        @Override
        public void send(OutputChannelEvent event) {
            if (event instanceof MessageOutputChannelEvent msgEvent) {
                var msg = msgEvent.getMessage();
                if (msg instanceof AssistantMessage) {
                    queue.offer(msg);
                }
            }
        }
    }
}
