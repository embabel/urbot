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
import com.embabel.urbot.event.ConversationAnalysisRequestEvent;
import com.embabel.urbot.proposition.extraction.ConversationPropositionExtraction;
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
    private ConversationPropositionExtraction propositionExtraction;

    @Autowired
    private DrivinePropositionRepository propositionRepository;

    private UrbotUser testUser;
    private String testContext;

    @BeforeEach
    void setUp() {
        testUser = new UrbotUser("it-test", "Test User", "testuser");
        testContext = "it_test_memory_" + System.currentTimeMillis();
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
        // -- Drive conversation --
        BlockingQueue<Message> responseQueue = new ArrayBlockingQueue<>(10);
        OutputChannel outputChannel = new CollectingOutputChannel(responseQueue);
        var chatSession = chatbot.createSession(testUser, outputChannel, null, null);

        String[] messages = {
                "I'm passionate about playing guitar, been playing since I was 12",
                "I work as a software engineer building distributed systems in Java",
                "My favorite food is sushi, I also love hiking on weekends",
                "I'm currently learning Rust for a side project"
        };

        for (String text : messages) {
            logger.info("Sending: {}", text);
            chatSession.onUserMessage(new UserMessage(text));

            Message response = responseQueue.poll(120, TimeUnit.SECONDS);
            assertNotNull(response, "Expected a response for message: " + text);
            logger.info("Response: {}", truncate(response.getContent(), 200));
        }

        // -- Trigger extraction synchronously --
        // The async @EventListener may also fire concurrently - that's fine,
        // the IncrementalAnalyzer deduplicates via content hash.
        logger.info("Triggering proposition extraction...");
        var event = new ConversationAnalysisRequestEvent(
                this, testUser, chatSession.getConversation());
        propositionExtraction.extractPropositions(event);

        // -- Assert propositions were persisted --
        List<Proposition> propositions = propositionRepository
                .findByContextIdValue(testUser.effectiveContext());

        logger.info("Found {} propositions for context {}", propositions.size(), testUser.effectiveContext());
        for (Proposition p : propositions) {
            logger.info("  [{}] confidence={} text='{}'", p.getStatus(), p.getConfidence(), p.getText());
        }

        // At least 2 propositions extracted
        assertTrue(propositions.size() >= 2,
                "Expected at least 2 propositions, got " + propositions.size());

        // All have correct contextId
        for (Proposition p : propositions) {
            assertEquals(testUser.effectiveContext(), p.getContextIdValue(),
                    "Proposition contextId mismatch");
        }

        // All have ACTIVE status
        for (Proposition p : propositions) {
            assertEquals(PropositionStatus.ACTIVE, p.getStatus(),
                    "Expected ACTIVE status for proposition: " + p.getText());
        }

        // All have positive confidence
        for (Proposition p : propositions) {
            assertTrue(p.getConfidence() > 0,
                    "Expected positive confidence for proposition: " + p.getText());
        }

        // At least 2 of the expected keywords appear across proposition texts
        String allText = propositions.stream()
                .map(Proposition::getText)
                .map(t -> t.toLowerCase(Locale.ROOT))
                .reduce("", (a, b) -> a + " " + b);

        String[] keywords = {"guitar", "software", "sushi", "hiking", "rust", "java"};
        long matchCount = 0;
        for (String keyword : keywords) {
            if (allText.contains(keyword)) {
                logger.info("  Keyword match: {}", keyword);
                matchCount++;
            }
        }

        assertTrue(matchCount >= 2,
                "Expected at least 2 keyword matches in proposition texts, got " + matchCount
                        + ". All text: " + allText);

        logger.info("Test passed: {} propositions with {} keyword matches", propositions.size(), matchCount);
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
