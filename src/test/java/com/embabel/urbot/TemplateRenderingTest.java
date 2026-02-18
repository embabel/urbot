package com.embabel.urbot;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.common.textio.template.JinjaProperties;
import com.embabel.common.textio.template.JinjavaTemplateRenderer;
import com.embabel.dice.common.KnownEntity;
import com.embabel.dice.common.SchemaAdherence;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.common.resolver.AlwaysCreateEntityResolver;
import com.embabel.dice.proposition.extraction.ExtractionPerspective;
import com.embabel.dice.proposition.extraction.TemplateModel;
import com.embabel.urbot.ChatbotOptions;
import com.embabel.urbot.user.UrbotUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates that all Jinja templates render without errors.
 * Catches template include failures (e.g., missing files from dice)
 * at unit test time rather than in integration tests.
 */
class TemplateRenderingTest {

    private JinjavaTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new JinjavaTemplateRenderer(
                new JinjaProperties("classpath:/prompts/", ".jinja", false),
                new DefaultResourceLoader()
        );
    }

    private TemplateModel createExtractionModel(UrbotUser user) {
        var schema = DataDictionary.fromClasses("urbot", UrbotUser.class);
        var context = SourceAnalysisContext
                .withContextId("test")
                .withEntityResolver(AlwaysCreateEntityResolver.INSTANCE)
                .withSchema(schema)
                .withKnownEntities(KnownEntity.asCurrentUser(user));
        return new TemplateModel(
                context,
                Chunk.create("User: I like hiking\nAssistant: That's great!", "test-source"),
                SchemaAdherence.DEFAULT,
                List.of(),
                ExtractionPerspective.ALL
        );
    }

    @Test
    void extractionTemplateRenders() {
        var user = new UrbotUser("test-user", "Test User", "tuser");
        var model = createExtractionModel(user);

        var result = renderer.renderLoadedTemplate(
                "dice/extract_urbot_user_propositions",
                Map.of("model", model, "user", user)
        );

        // Verify core content renders
        assertTrue(result.contains("Test User"), "Should contain user name");
        assertTrue(result.contains("I like hiking"), "Should contain conversation text");

        // Verify includes from dice resolved
        assertTrue(result.contains("Canonical Form"), "Should include canonical_form.jinja from dice");
        assertTrue(result.contains("simple present tense"), "Canonical form content should render");

        // Verify user-specific canonical rule
        assertTrue(result.contains("Start with the user's full name"), "Should have user-specific canonical rule");
    }

    @Test
    void urbotSystemPromptRenders() {
        var user = new UrbotUser("test-user", "Test User", "tuser");
        var chat = new ChatbotOptions(null, 20, "qa", "default", "assistant", 200, 50, true, true, "Chatbot with RAG and memory");
        var properties = new UrbotProperties(chat, null, null, null, List.of(), List.of(), "", "");

        var result = renderer.renderLoadedTemplate(
                "urbot",
                Map.of("properties", properties, "user", user)
        );

        assertFalse(result.isEmpty(), "System prompt should not be empty");
        assertTrue(result.contains("200"), "Should contain maxWords from voice config");
        assertTrue(result.contains("Test User"), "Should contain user name");
    }
}
