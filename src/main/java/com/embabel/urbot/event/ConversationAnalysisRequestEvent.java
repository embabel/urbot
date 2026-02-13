package com.embabel.urbot.event;

import com.embabel.chat.Conversation;
import com.embabel.urbot.user.UrbotUser;
import org.springframework.context.ApplicationEvent;

/**
 * Event published after a conversation exchange (user message + assistant response).
 * Used to trigger async proposition extraction.
 */
public class ConversationAnalysisRequestEvent extends ApplicationEvent {

    public final UrbotUser user;
    public final Conversation conversation;

    public ConversationAnalysisRequestEvent(
            Object source,
            UrbotUser user,
            Conversation conversation) {
        super(source);
        this.user = user;
        this.conversation = conversation;
    }
}
