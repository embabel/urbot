package com.embabel.urbot;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.urbot.proposition.PropositionExtractionProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Properties for chatbot
 *
 * @param chatLlm                LLM model and hyperparameters to use
 * @param messagesToEmbed        how many recent messages to include in the context for RAG retrieval and proposition extraction
 * @param objective              the goal of the chatbot's responses
 * @param behaviour              the behaviour profile to use
 * @param voice                  the persona and output style of the chatbot
 * @param chunkerConfig          configuration for ingestion
 * @param neoRag                 Neo4j RAG service configuration
 * @param propositionExtraction  proposition extraction configuration
 * @param initialDocuments       list of document URIs to ingest into the global context at startup
 *                               if not already loaded. Each entry can be a URL (e.g., "https://example.com/doc.pdf")
 *                               or a file path (absolute or relative to the working directory).
 */
@ConfigurationProperties(prefix = "urbot")
public record UrbotProperties(
        @NestedConfigurationProperty LlmOptions chatLlm,
        @DefaultValue("20") int messagesToEmbed,
        String objective,
        String behaviour,
        @NestedConfigurationProperty Voice voice,
        @NestedConfigurationProperty ContentChunker.Config chunkerConfig,
        @NestedConfigurationProperty NeoRagServiceProperties neoRag,
        @NestedConfigurationProperty PropositionExtractionProperties propositionExtraction,
        List<String> initialDocuments
) {

    public UrbotProperties {
        if (neoRag == null) {
            neoRag = new NeoRagServiceProperties();
        }
        if (propositionExtraction == null) {
            propositionExtraction = new PropositionExtractionProperties(
                    null, null, 10, 2, 6, false, false, List.of("com.embabel.personal")
            );
        }
    }

    public record Voice(
            String persona,
            int maxWords
    ) {
    }
}
