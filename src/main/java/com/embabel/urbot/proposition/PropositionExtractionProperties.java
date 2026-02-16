package com.embabel.urbot.proposition;

import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configuration for proposition extraction from conversations.
 *
 * @param extractionLlm       LLM for proposition extraction
 * @param entityResolutionLlm LLM for entity resolution
 * @param windowSize          number of messages to include in each extraction window
 * @param overlapSize         number of messages to overlap for context continuity
 * @param triggerInterval     extract propositions every N messages (0 = manual only)
 * @param showPrompts         whether to log extraction prompts
 * @param showResponses       whether to log extraction responses
 * @param entityPackages      packages to scan for NamedEntity classes to include in the data dictionary
 */
public record PropositionExtractionProperties(
        @DefaultValue("true") boolean enabled,
        @NestedConfigurationProperty LlmOptions extractionLlm,
        @NestedConfigurationProperty LlmOptions entityResolutionLlm,
        @DefaultValue("10") int windowSize,
        @DefaultValue("2") int overlapSize,
        @DefaultValue("6") int triggerInterval,
        @DefaultValue("false") boolean showPrompts,
        @DefaultValue("false") boolean showResponses,
        @DefaultValue("") List<String> entityPackages
) {
    public PropositionExtractionProperties {
        if (windowSize <= 0) windowSize = 10;
        if (overlapSize < 0) overlapSize = 2;
        if (triggerInterval < 0) triggerInterval = 6;
    }
}
