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
 * @param chatLlm          LLM model and hyperparameters to use
 * @param messagesToEmbed  how many recent messages to include in the context for RAG retrieval and proposition extraction
 * @param objective        the goal of the chatbot's responses
 * @param behaviour        the behaviour profile to use
 * @param persona          the persona and output style of the chatbot. A template
 * @param maxWords         maximum number of words to use in the chatbot's response. This is a soft limit and may be exceeded if necessary to fulfill the objective.
 * @param ingestion        configuration for ingestion
 * @param neoRag           Neo4j RAG service configuration
 * @param memory           proposition extraction configuration
 * @param botPackages      additional packages to scan for bot components (e.g., "com.embabel.bot").
 *                         Beans in these packages should be gated with {@code @Profile}.
 * @param initialDocuments list of document URIs to ingest into the global context at startup
 *                         if not already loaded. Each entry can be a URL (e.g., "https://example.com/doc.pdf")
 *                         or a file path (absolute or relative to the working directory).
 */
@ConfigurationProperties(prefix = "urbot")
public record UrbotProperties(
        @NestedConfigurationProperty LlmOptions chatLlm,
        @DefaultValue("20") int messagesToEmbed,
        String objective,
        String behaviour,
        String persona,
        int maxWords,
        @NestedConfigurationProperty ContentChunker.Config ingestion,
        @NestedConfigurationProperty NeoRagServiceProperties neoRag,
        @NestedConfigurationProperty PropositionExtractionProperties memory,
        @DefaultValue("") List<String> botPackages,
        List<String> initialDocuments
) {

    public UrbotProperties {
        if (neoRag == null) {
            neoRag = new NeoRagServiceProperties();
        }
    }

}
