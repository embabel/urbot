package com.embabel.urbot;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * Properties for chatbot
 *
 * @param chatLlm                  LLM model and hyperparameters to use
 * @param objective                the goal of the chatbot's responses
 * @param voice                    the persona and output style of the chatbot
 * @param chunkerConfig            configuration for ingestion
 * @param neoRag                   Neo4j RAG service configuration
 * @param propositionExtractionLlm LLM for proposition extraction
 * @param entityResolutionLlm      LLM for entity resolution
 * @param extraction               extraction window configuration
 * @param showExtractionPrompts    whether to log extraction prompts
 * @param showExtractionResponses  whether to log extraction responses
 * @param initialDocuments         list of document URIs to ingest into the global context at startup
 *                                  if not already loaded. Each entry can be a URL (e.g., "https://example.com/doc.pdf")
 *                                  or a file path (absolute or relative to the working directory).
 */
@ConfigurationProperties(prefix = "urbot")
public record UrbotProperties(
        @NestedConfigurationProperty LlmOptions chatLlm,
        String objective,
        @NestedConfigurationProperty Voice voice,
        @NestedConfigurationProperty ContentChunker.Config chunkerConfig,
        @NestedConfigurationProperty NeoRagServiceProperties neoRag,
        @NestedConfigurationProperty LlmOptions propositionExtractionLlm,
        @NestedConfigurationProperty LlmOptions entityResolutionLlm,
        @NestedConfigurationProperty Extraction extraction,
        boolean showExtractionPrompts,
        boolean showExtractionResponses,
        List<String> initialDocuments
) {

    public UrbotProperties {
        if (neoRag == null) {
            neoRag = new NeoRagServiceProperties();
        }
        if (extraction == null) {
            extraction = new Extraction(10, 2, 6);
        }
    }

    public record Voice(
            String persona,
            int maxWords
    ) {
    }

    /**
     * Configuration for proposition extraction from conversations.
     *
     * @param windowSize      number of messages to include in each extraction window
     * @param overlapSize     number of messages to overlap for context continuity
     * @param triggerInterval extract propositions every N messages (0 = manual only)
     */
    public record Extraction(
            int windowSize,
            int overlapSize,
            int triggerInterval
    ) {
        public Extraction {
            if (windowSize <= 0) windowSize = 10;
            if (overlapSize < 0) overlapSize = 2;
            if (triggerInterval < 0) triggerInterval = 6;
        }
    }
}
