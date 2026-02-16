package com.embabel.urbot.vaadin;

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.agent.rag.model.NamedEntityData;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.Set;

/**
 * Reusable panel displaying a resolved entity with its type, name, and description.
 */
public class EntityPanel extends Div {

    private final NamedEntity entity;

    public EntityPanel(NamedEntity entity) {
        this.entity = entity;
        addClassName("entity-card");

        var headerLayout = new HorizontalLayout();
        headerLayout.setSpacing(true);
        headerLayout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        headerLayout.addClassName("entity-header");

        var typeBadge = new Span(getPrimaryLabel(entity.labels()));
        typeBadge.addClassName("entity-type-badge");

        var nameSpan = new Span(entity.getName());
        nameSpan.addClassName("entity-name");

        headerLayout.add(typeBadge, nameSpan);

        var descSpan = new Span(entity.getDescription());
        descSpan.addClassName("entity-description");

        var idSpan = new Span("id: " + entity.getId());
        idSpan.addClassName("entity-id");

        add(headerLayout, descSpan, idSpan);
    }

    private String getPrimaryLabel(Set<String> labels) {
        return labels.stream()
                .filter(l -> !l.equals(NamedEntityData.ENTITY_LABEL) && !l.equals("Reference"))
                .findFirst()
                .orElse(labels.stream().findFirst().orElse(NamedEntityData.ENTITY_LABEL));
    }

    public NamedEntity getEntity() {
        return entity;
    }
}
