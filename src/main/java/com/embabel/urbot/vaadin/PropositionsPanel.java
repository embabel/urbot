package com.embabel.urbot.vaadin;

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Panel showing the knowledge base of extracted propositions.
 */
public class PropositionsPanel extends VerticalLayout {

    private final PropositionRepository propositionRepository;
    private final Function<String, NamedEntity> entityResolver;
    private final VerticalLayout propositionsContent;
    private final Span propositionCountSpan;
    private Consumer<String> onDelete;
    private String contextId;

    public PropositionsPanel(PropositionRepository propositionRepository,
                             Function<String, NamedEntity> entityResolver) {
        this.propositionRepository = propositionRepository;
        this.entityResolver = entityResolver;

        setPadding(false);
        setSpacing(true);
        setSizeFull();

        var headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.setSpacing(true);
        headerLayout.setWidthFull();

        var titleSpan = new Span("Memories");
        titleSpan.addClassName("panel-title");

        propositionCountSpan = new Span("(0 memories)");
        propositionCountSpan.addClassName("panel-count");

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.getElement().setAttribute("title", "Refresh memories");
        refreshButton.addClickListener(e -> refresh());

        headerLayout.add(titleSpan, propositionCountSpan, refreshButton);
        headerLayout.setFlexGrow(1, titleSpan);

        propositionsContent = new VerticalLayout();
        propositionsContent.setPadding(false);
        propositionsContent.setSpacing(true);
        propositionsContent.setWidthFull();

        var contentScroller = new Scroller(propositionsContent);
        contentScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        contentScroller.setSizeFull();
        contentScroller.addClassName("panel-scroller");

        add(headerLayout, contentScroller);
        setFlexGrow(1, contentScroller);
    }

    public void refresh() {
        propositionsContent.removeAll();

        var propositions = contextId != null
                ? propositionRepository.findByContextIdValue(contextId)
                : propositionRepository.findAll();
        propositionCountSpan.setText("(" + propositions.size() + " memories)");

        if (propositions.isEmpty()) {
            var emptyMessage = new Span("No memories yet. Start a conversation and analyze it to build memories.");
            emptyMessage.addClassName("panel-empty-message");
            propositionsContent.add(emptyMessage);
            return;
        }

        propositions.stream()
                .sorted(Comparator.comparing(Proposition::getCreated).reversed())
                .forEach(prop -> {
                    var card = new PropositionCard(prop, entityResolver);
                    if (onDelete != null) {
                        card.setOnDelete(p -> {
                            onDelete.accept(p.getId());
                            refresh();
                        });
                    }
                    propositionsContent.add(card);
                });
    }

    public void setOnDelete(Consumer<String> handler) {
        this.onDelete = handler;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public void scheduleRefresh(com.vaadin.flow.component.UI ui, long delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                ui.access(this::refresh);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
