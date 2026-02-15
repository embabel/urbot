package com.embabel.urbot.event;

import com.embabel.chat.Message;
import com.embabel.dice.incremental.IncrementalSource;
import com.embabel.urbot.user.UrbotUser;
import org.springframework.context.ApplicationEvent;

public abstract class SourceAnalysisRequestEvent extends ApplicationEvent {

    public final UrbotUser user;

    public SourceAnalysisRequestEvent(
            Object source,
            UrbotUser user
    ) {
        super(source);
        this.user = user;
    }

    public abstract IncrementalSource<Message> incrementalSource();
}
