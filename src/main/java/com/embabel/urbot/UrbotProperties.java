package com.embabel.urbot;

import com.embabel.agent.rag.ingestion.ContentChunker;
import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Properties for chatbot
 *
 * @param chatLlm       LLM model and hyperparameters to use
 * @param objective     the goal of the chatbot's responses
 * @param voice         the persona and output style of the chatbot
 * @param chunkerConfig configuration for ingestion
 * @param neoRag        Neo4j RAG service configuration
 */
@ConfigurationProperties(prefix = "urbot")
public record UrbotProperties(
        @NestedConfigurationProperty LlmOptions chatLlm,
        String objective,
        @NestedConfigurationProperty Voice voice,
        @NestedConfigurationProperty ContentChunker.Config chunkerConfig,
        @NestedConfigurationProperty NeoRagServiceProperties neoRag
) {

    public UrbotProperties {
        if (neoRag == null) {
            neoRag = new NeoRagServiceProperties();
        }
    }

    public record Voice(
            String persona,
            int maxWords
    ) {
    }
}
