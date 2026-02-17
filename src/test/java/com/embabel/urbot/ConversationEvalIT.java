package com.embabel.urbot;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.eval.client.MessageRole;
import com.embabel.agent.eval.support.*;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;
import com.embabel.common.textio.template.JinjavaTemplateRenderer;
import com.embabel.dice.proposition.Proposition;
import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import com.embabel.urbot.user.UrbotUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that evaluates urbot conversation quality using LLM-as-judge scoring
 * from the embabel-agent-eval module.
 * <p>
 * Two-phase test:
 * <ol>
 *   <li>Seed memory via conversation and proposition extraction</li>
 *   <li>Evaluate recall in a new session, scoring with {@link TranscriptScorer}</li>
 * </ol>
 */
@SpringBootTest(
        classes = TestUrbotApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("it")
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class ConversationEvalIT {

    private static final Logger logger = LoggerFactory.getLogger(ConversationEvalIT.class);

    private static final String USER_ID = "it-eval";
    private static final String CONTEXT_PREFIX = USER_ID + "_it_eval_";

    @Autowired
    private Chatbot chatbot;

    @Autowired
    private DrivinePropositionRepository propositionRepository;

    private UrbotUser testUser;

    @BeforeEach
    void setUp() {
        // Clean ALL eval contexts (catches orphans from timed-out or crashed runs)
        int deleted = propositionRepository.clearByContextPrefix(CONTEXT_PREFIX);
        if (deleted > 0) {
            logger.info("Cleaned up {} stale propositions from prior eval runs", deleted);
        }

        testUser = new UrbotUser(USER_ID, "Claudia Carter", "ccarter");
        var testContext = "it_eval_" + System.currentTimeMillis();
        testUser.setCurrentContextName(testContext);

        logger.info("Eval test context: {} (effectiveContext={})", testContext, testUser.effectiveContext());
    }

    @AfterEach
    void tearDown() {
        if (testUser != null) {
            int deleted = propositionRepository.clearByContext(testUser.effectiveContext());
            logger.info("Cleaned up {} propositions for context {}", deleted, testUser.effectiveContext());
        }
    }

    @Test
    @DisplayName("Short memory recall evaluation")
    void shortMemoryRecall() throws Exception {
        testEvalConfig(loadConfig("eval/short-conversation-eval.yml"));
    }

    @Test
    @DisplayName("Long memory recall evaluation")
    void longMemoryRecall() throws Exception {
        testEvalConfig(loadConfig("eval/long-conversation-eval.yml"));
    }

    /**
     * Runs the full seed → eval → score pipeline for a given config.
     */
    void testEvalConfig(EvalConfig config) throws Exception {
        // -- Phase 1: Seed memory --
        logger.info("=== Phase 1: Seeding memory ({} seeds) ===", config.seeds().size());
        seedMemory(config);

        // -- Phase 2: Evaluate recall in new session --
        logger.info("=== Phase 2: Evaluating recall ({} tasks) ===", config.tasks().size());
        var evalTranscript = evaluateRecall(config);

        // -- Phase 3: Score with LLM judge --
        logger.info("=== Phase 3: LLM-as-judge scoring ===");
        var apiKey = System.getenv("OPENAI_API_KEY");
        assertNotNull(apiKey, "OPENAI_API_KEY must be set for LLM-as-judge scoring");
        var scoringChatModel = OpenAiChatModel.builder()
                .openAiApi(new OpenAiApi.Builder().apiKey(apiKey).build())
                .build();
        var scorer = new TranscriptScorer(scoringChatModel, new JinjavaTemplateRenderer());

        var scores = scorer.scoreConversation(config.tasks(), config.facts(), evalTranscript);

        // -- Write report --
        writeReport(config, evalTranscript, scores);

        // -- Assertions --
        logger.info("Tone score: {}", scores.getTone());
        for (var taskScore : scores.getTasks()) {
            logger.info("Task score: {} = {}", taskScore.getScored(), taskScore.getScore());
            assertTrue(taskScore.getScore() >= config.effectiveMinTaskScore(),
                    "Task score too low for '" + taskScore.getScored() + "': " + taskScore.getScore()
                            + " (min: " + config.effectiveMinTaskScore() + ")");
        }

        double average = scores.averageTaskScore();
        logger.info("Average task score: {}", average);
        assertTrue(average >= config.effectiveMinAverageScore(),
                "Average task score too low: " + average + " (min: " + config.effectiveMinAverageScore() + ")");
    }

    // ---- Phase helpers ----

    private void seedMemory(EvalConfig config) throws Exception {
        boolean firstSeedChecked = false;

        for (int i = 0; i < config.seeds().size(); i++) {
            var seed = config.seeds().get(i);
            switch (seed) {
                case ConversationSeed cs -> seedFromConversation(cs);
                case TextSeed ts -> fail("TextSeed not yet supported in urbot IT: " + ts.getText());
            }

            // Fail fast after first seed: if extraction is broken (e.g., template errors),
            // don't waste time seeding 9 more conversations.
            // Poll rather than fixed sleep — extraction is async and timing varies.
            if (!firstSeedChecked) {
                firstSeedChecked = true;
                int count = 0;
                for (int attempt = 0; attempt < 10; attempt++) {
                    Thread.sleep(3000);
                    count = propositionRepository.findByContextIdValue(testUser.effectiveContext()).size();
                    if (count > 0) break;
                }
                if (count == 0) {
                    fail("No propositions extracted after first seed — likely a template error in extraction prompts. "
                            + "Check logs for InvalidTemplateException.");
                }
                logger.info("Fail-fast check passed: {} propositions after first seed", count);
            }
        }

        // Wait for async extraction to settle — the chatbot fires
        // ConversationAnalysisRequestEvent after each exchange, so
        // extraction is already running in the background.
        waitForExtraction();

        var propositions = propositionRepository.findByContextIdValue(testUser.effectiveContext());
        logPropositions("After seeding", propositions);
        assertTrue(propositions.size() >= 2,
                "Expected at least 2 propositions from seed, got " + propositions.size());
    }

    private void seedFromConversation(ConversationSeed seed) throws Exception {
        BlockingQueue<Message> responseQueue = new ArrayBlockingQueue<>(10);
        OutputChannel outputChannel = new CollectingOutputChannel(responseQueue);
        var chatSession = chatbot.createSession(testUser, outputChannel, null, null);

        for (var msg : seed.getConversation()) {
            if (msg.getRole() == MessageRole.user) {
                logger.info("Seed: {}", msg.getContent());
                chatSession.onUserMessage(new UserMessage(msg.getContent()));

                Message response = responseQueue.poll(120, TimeUnit.SECONDS);
                assertNotNull(response, "Expected a response for seed message: " + msg.getContent());
                logger.info("Response: {}", truncate(response.getContent(), 200));
            }
        }
    }

    /**
     * Poll until async proposition extraction has settled
     * (no new propositions appearing for 10 seconds).
     */
    private void waitForExtraction() throws InterruptedException {
        int stableCount = 0;
        int lastCount = -1;
        while (stableCount < 5) {
            Thread.sleep(2000);
            int current = propositionRepository.findByContextIdValue(testUser.effectiveContext()).size();
            if (current == lastCount) {
                stableCount++;
            } else {
                logger.info("Extraction in progress: {} propositions so far", current);
                stableCount = 0;
                lastCount = current;
            }
        }
    }

    /**
     * Creates a NEW chat session (no shared chat history with the seed phase).
     * The bot can only answer from persisted propositions in Neo4j,
     * loaded via {@code Memory.forContext(user.currentContext())} in ChatActions.
     */
    private List<TimedOpenAiCompatibleMessage> evaluateRecall(EvalConfig config) throws Exception {
        BlockingQueue<Message> responseQueue = new ArrayBlockingQueue<>(10);
        OutputChannel outputChannel = new CollectingOutputChannel(responseQueue);
        var chatSession = chatbot.createSession(testUser, outputChannel, null, null);

        List<TimedOpenAiCompatibleMessage> transcript = new ArrayList<>();

        for (var task : config.tasks()) {
            logger.info("Eval task: {}", task.getTask());
            chatSession.onUserMessage(new UserMessage(task.getTask()));

            transcript.add(new TimedOpenAiCompatibleMessage(
                    task.getTask(), MessageRole.user, 0L, List.of()));

            Message response = responseQueue.poll(120, TimeUnit.SECONDS);
            assertNotNull(response, "No response for eval task: " + task.getTask());
            logger.info("Eval response: {}", truncate(response.getContent(), 300));

            transcript.add(new TimedOpenAiCompatibleMessage(
                    response.getContent(), MessageRole.assistant, 0L, List.of()));
        }

        return transcript;
    }

    // ---- Report ----

    private void writeReport(
            EvalConfig config,
            List<TimedOpenAiCompatibleMessage> transcript,
            SubjectiveScores scores) throws IOException {

        var outputDir = Path.of("target", "it-results");
        Files.createDirectories(outputDir);
        var outputFile = outputDir.resolve("eval-report-" + Instant.now().toEpochMilli() + ".txt");

        var sb = new StringBuilder();
        sb.append("# Conversation Evaluation Report\n");
        sb.append("# Generated: ").append(Instant.now()).append("\n\n");

        sb.append("## Scores\n");
        sb.append(String.format("Tone: %.2f%n", scores.getTone()));
        for (var taskScore : scores.getTasks()) {
            sb.append(String.format("Task '%.60s': %.2f%n", taskScore.getScored(), taskScore.getScore()));
        }
        sb.append(String.format("Average: %.2f%n%n", scores.averageTaskScore()));

        sb.append("## Ground Truth Facts\n");
        for (var fact : config.facts()) {
            sb.append("  - ").append(fact).append("\n");
        }
        sb.append("\n");

        sb.append("## Eval Transcript\n");
        for (var msg : transcript) {
            sb.append(String.format("[%s] %s%n", msg.getRole(), msg.getContent()));
        }

        Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
        logger.info("Wrote eval report to {}", outputFile);
    }

    // ---- Config loading ----

    /**
     * YAML config with seeds, eval tasks, and ground truth facts.
     * Uses eval module types: {@link Seed}, {@link Task}.
     */
    record EvalConfig(
            List<Seed> seeds,
            List<Task> tasks,
            List<String> facts,
            Double minTaskScore,
            Double minAverageScore
    ) {
        double effectiveMinTaskScore() {
            return minTaskScore != null ? minTaskScore : 0.5;
        }

        double effectiveMinAverageScore() {
            return minAverageScore != null ? minAverageScore : 0.6;
        }
    }

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new KotlinModule.Builder().build());

    private static EvalConfig loadConfig(String resourceName) throws IOException {
        try (var in = ConversationEvalIT.class.getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(in, "Config not found on classpath: " + resourceName);
            var config = YAML.readValue(in, EvalConfig.class);
            assertFalse(config.seeds().isEmpty(), "Config has no seeds");
            assertFalse(config.tasks().isEmpty(), "Config has no eval tasks");
            assertFalse(config.facts().isEmpty(), "Config has no facts");
            return config;
        }
    }

    // ---- Utilities ----

    private void logPropositions(String label, List<Proposition> propositions) {
        var sorted = propositions.stream()
                .sorted(java.util.Comparator.comparing(Proposition::getText))
                .toList();
        logger.info("=== {} ({} propositions) ===", label, sorted.size());
        for (var p : sorted) {
            logger.info("  [{}] confidence={} text='{}'", p.getStatus(), p.getConfidence(), p.getText());
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

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
