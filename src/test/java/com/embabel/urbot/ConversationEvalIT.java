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
import com.embabel.urbot.event.ConversationAnalysisRequestEvent;
import com.embabel.urbot.proposition.extraction.ConversationPropositionExtraction;
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

    @Autowired
    private Chatbot chatbot;

    @Autowired
    private ConversationPropositionExtraction propositionExtraction;

    @Autowired
    private DrivinePropositionRepository propositionRepository;

    private UrbotUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new UrbotUser("it-eval", "Claudia Carter", "ccarter");
        var testContext = "it_eval_" + System.currentTimeMillis();
        testUser.setCurrentContextName(testContext);

        logger.info("Eval test context: {} (effectiveContext={})", testContext, testUser.effectiveContext());

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
            assertTrue(taskScore.getScore() >= 0.5,
                    "Task score too low for '" + taskScore.getScored() + "': " + taskScore.getScore());
        }

        double average = scores.averageTaskScore();
        logger.info("Average task score: {}", average);
        assertTrue(average >= 0.6,
                "Average task score too low: " + average);
    }

    // ---- Phase helpers ----

    private void seedMemory(EvalConfig config) throws Exception {
        for (var seed : config.seeds()) {
            switch (seed) {
                case ConversationSeed cs -> seedFromConversation(cs);
                case TextSeed ts -> fail("TextSeed not yet supported in urbot IT: " + ts.getText());
            }
        }

        var propositions = propositionRepository.findByContextIdValue(testUser.effectiveContext());
        logger.info("Seeded {} propositions", propositions.size());
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
            // assistant messages in seed are ignored — we use the live agent's responses
        }

        // Trigger proposition extraction — the incremental analyzer may skip
        // on the first call if it determines the window isn't ready, so retry once.
        var event = new ConversationAnalysisRequestEvent(
                this, testUser, chatSession.getConversation());
        propositionExtraction.extractPropositions(event);

        if (propositionRepository.findByContextIdValue(testUser.effectiveContext()).isEmpty()) {
            logger.info("First extraction yielded 0 propositions, retrying...");
            propositionExtraction.extractPropositions(event);
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
    record EvalConfig(List<Seed> seeds, List<Task> tasks, List<String> facts) {
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
