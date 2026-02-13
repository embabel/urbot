package com.embabel.urbot.vaadin;

import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.Proposition;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Card component displaying a single proposition with its metadata.
 */
public class PropositionCard extends Div {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Proposition proposition;
    private Consumer<EntityMention> onMentionClick;
    private Consumer<Proposition> onDelete;
    private HorizontalLayout entitiesLayout;

    public PropositionCard(Proposition prop) {
        this.proposition = prop;
        addClassName("proposition-card");

        var headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setSpacing(true);
        headerLayout.addClassName("proposition-header");

        var textSpan = new Span(prop.getText());
        textSpan.addClassName("proposition-text");

        var deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteButton.addClassName("proposition-delete");
        deleteButton.getElement().setAttribute("title", "Delete this memory");
        deleteButton.addClickListener(e -> {
            if (onDelete != null) {
                onDelete.accept(proposition);
            }
        });

        headerLayout.add(textSpan, deleteButton);
        headerLayout.setFlexGrow(1, textSpan);

        var metaLayout = new HorizontalLayout();
        metaLayout.setSpacing(true);
        metaLayout.addClassName("proposition-meta");

        var confidencePercent = (int) (prop.getConfidence() * 100);
        var confidenceSpan = new Span(confidencePercent + "% confidence");
        confidenceSpan.addClassName("proposition-confidence");
        confidenceSpan.addClassName(confidencePercent >= 80 ? "high" :
                confidencePercent >= 50 ? "medium" : "low");

        var timeSpan = new Span(TIME_FORMATTER.format(prop.getCreated()));
        timeSpan.addClassName("proposition-time");

        metaLayout.add(confidenceSpan, timeSpan);

        var mentions = prop.getMentions();
        if (!mentions.isEmpty()) {
            entitiesLayout = new HorizontalLayout();
            entitiesLayout.setSpacing(false);
            entitiesLayout.addClassName("proposition-entities");

            for (var mention : mentions) {
                entitiesLayout.add(createMentionBadge(mention));
            }
            add(headerLayout, metaLayout, entitiesLayout);
        } else {
            add(headerLayout, metaLayout);
        }
    }

    private Span createMentionBadge(EntityMention mention) {
        var id = mention.getResolvedId() != null ? mention.getResolvedId() : "?";
        var badge = new Span(mention.getType() + ":" + id);
        badge.addClassName("mention-badge");

        if (mention.getResolvedId() != null) {
            badge.addClassName("clickable");
            badge.getElement().addEventListener("click", e -> {
                if (onMentionClick != null) {
                    onMentionClick.accept(mention);
                }
            });
        }

        return badge;
    }

    public void setOnMentionClick(Consumer<EntityMention> handler) {
        this.onMentionClick = handler;
    }

    public void setOnDelete(Consumer<Proposition> handler) {
        this.onDelete = handler;
    }

    public Proposition getProposition() {
        return proposition;
    }
}
