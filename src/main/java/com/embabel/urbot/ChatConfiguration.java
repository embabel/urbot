package com.embabel.urbot;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Verbosity;
import com.embabel.chat.Chatbot;
import com.embabel.chat.agent.AgentProcessChatbot;
import com.embabel.chat.support.InMemoryConversationFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure a chatbot that uses all actions available on the AgentPlatform
 */
@Configuration
class ChatConfiguration {

    @Bean
    Chatbot chatbot(AgentPlatform agentPlatform, UrbotProperties properties) {
        var verbosity = new Verbosity()
                .withShowPrompts(properties.chat().showPrompts())
                .withShowLlmResponses(properties.chat().showResponses());
        return AgentProcessChatbot.utilityFromPlatform(
                agentPlatform,
                new InMemoryConversationFactory(),
                verbosity
        );
    }
}
