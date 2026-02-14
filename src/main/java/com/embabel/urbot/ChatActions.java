package com.embabel.urbot;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.filter.PropertyFilter;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.dice.agent.Memory;
import com.embabel.dice.projection.memory.MemoryProjector;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.urbot.event.ConversationAnalysisRequestEvent;
import com.embabel.urbot.rag.DocumentService;
import com.embabel.urbot.user.UrbotUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

/**
 * The platform can use any action to respond to user messages.
 */
@EmbabelComponent
public class ChatActions {

    private final Logger logger = LoggerFactory.getLogger(ChatActions.class);

    private final SearchOperations searchOperations;
    private final UrbotProperties properties;
    private final LlmReference globalDocuments;
    private final MemoryProjector memoryProjector;
    private final PropositionRepository propositionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ChatActions(
            SearchOperations searchOperations,
            @Qualifier("globalDocuments") LlmReference globalDocuments,
            UrbotProperties properties,
            MemoryProjector memoryProjector,
            PropositionRepository propositionRepository,
            ApplicationEventPublisher eventPublisher) {
        this.searchOperations = searchOperations;
        this.globalDocuments = globalDocuments;
        this.properties = properties;
        this.memoryProjector = memoryProjector;
        this.propositionRepository = propositionRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Bind user to AgentProcess. Will run once at the start of the process.
     */
    @Action
    UrbotUser bindUser(OperationContext context) {
        var forUser = context.getProcessContext().getProcessOptions().getIdentities().getForUser();
        if (forUser instanceof UrbotUser su) {
            return su;
        } else {
            logger.warn("bindUser: forUser is not an UrbotUser: {}", forUser);
            return null;
        }
    }

    @Action(
            canRerun = true,
            trigger = UserMessage.class
    )
    void respond(
            Conversation conversation,
            UrbotUser user,
            ActionContext context) {
        var userDocuments = new ToolishRag(
                "user_docs",
                "User's own documents",
                searchOperations)
                .withMetadataFilter(
                        new PropertyFilter.Eq(
                                DocumentService.Context.CONTEXT_KEY,
                                user.effectiveContext()
                        ));

        var memory = Memory.forContext(user.currentContext())
                .withRepository(propositionRepository)
                .withEagerQuery(q -> q.orderedByEffectiveConfidence().withLimit(10))
                .withProjector(memoryProjector);

        var assistantMessage = context.
                ai()
                .withLlm(properties.chatLlm())
                .withId("chat_response")
                .withReferences(globalDocuments, userDocuments, memory)
                .withTools(memory)
                .rendering("urbot")
                .respondWithSystemPrompt(conversation, Map.of(
                        "properties", properties
                ));
        context.sendMessage(conversation.addMessage(assistantMessage));

        eventPublisher.publishEvent(new ConversationAnalysisRequestEvent(this, user, conversation));
    }
}
