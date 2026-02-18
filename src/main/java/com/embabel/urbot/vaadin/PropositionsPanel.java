package com.embabel.urbot.vaadin;

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.agent.rag.service.Cluster;
import com.embabel.common.core.types.SimilarityResult;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Panel showing the knowledge base of extracted propositions.
 */
public class PropositionsPanel extends VerticalLayout {

    private final DrivinePropositionRepository propositionRepository;
    private final Function<String, NamedEntity> entityResolver;
    private final VerticalLayout propositionsContent;
    private final Span propositionCountSpan;
    private final Button clusterToggle;
    private Consumer<String> onDelete;
    private String contextId;
    private boolean clustered = false;

    public PropositionsPanel(DrivinePropositionRepository propositionRepository,
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

        clusterToggle = new Button("Clusters", VaadinIcon.CLUSTER.create());
        clusterToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        clusterToggle.addClassName("cluster-toggle");
        clusterToggle.getElement().setAttribute("title", "Toggle cluster view");
        clusterToggle.addClickListener(e -> {
            clustered = !clustered;
            clusterToggle.setText(clustered ? "List" : "Clusters");
            clusterToggle.setIcon(clustered ? VaadinIcon.LIST.create() : VaadinIcon.CLUSTER.create());
            refresh();
        });

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.getElement().setAttribute("title", "Refresh memories");
        refreshButton.addClickListener(e -> refresh());

        headerLayout.add(titleSpan, propositionCountSpan, clusterToggle, refreshButton);
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
        if (clustered) {
            refreshClustered();
        } else {
            refreshFlat();
        }
    }

    private void refreshFlat() {
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
                .forEach(prop -> propositionsContent.add(createCard(prop)));
    }

    private void refreshClustered() {
        var query = PropositionQuery.againstContext(contextId);
        List<Cluster<Proposition>> clusters = propositionRepository.findClusters(0.7, 10, query);

        // Collect all propositions that appear in a cluster
        var clusteredIds = new HashSet<String>();
        for (var cluster : clusters) {
            clusteredIds.add(cluster.getAnchor().getId());
            for (var sim : cluster.getSimilar()) {
                clusteredIds.add(sim.getMatch().getId());
            }
        }

        // Get all propositions so we can find unclustered ones
        var allPropositions = contextId != null
                ? propositionRepository.findByContextIdValue(contextId)
                : propositionRepository.findAll();

        int totalCount = allPropositions.size();
        propositionCountSpan.setText("(" + totalCount + " memories, " + clusters.size() + " clusters)");

        if (allPropositions.isEmpty()) {
            var emptyMessage = new Span("No memories yet. Start a conversation and analyze it to build memories.");
            emptyMessage.addClassName("panel-empty-message");
            propositionsContent.add(emptyMessage);
            return;
        }

        // Render each cluster as a collapsible Details component
        for (var cluster : clusters) {
            var anchor = cluster.getAnchor();
            List<SimilarityResult<Proposition>> similar = cluster.getSimilar();

            // Summary: anchor text + count badge
            var summaryLayout = new HorizontalLayout();
            summaryLayout.setAlignItems(Alignment.CENTER);
            summaryLayout.setSpacing(true);
            summaryLayout.addClassName("cluster-summary");

            var anchorText = new Span(truncate(anchor.getText(), 80));
            anchorText.addClassName("proposition-text");

            var countBadge = new Span((similar.size() + 1) + "");
            countBadge.addClassName("similarity-badge");
            countBadge.addClassName("cluster-count");

            summaryLayout.add(anchorText, countBadge);

            // Content: anchor card + similar cards with similarity scores
            var content = new VerticalLayout();
            content.setPadding(false);
            content.setSpacing(true);
            content.addClassName("cluster-content");

            content.add(createCard(anchor));

            for (var sim : similar) {
                var simCard = createCard(sim.getMatch());
                var scorePct = (int) Math.round(sim.getScore() * 100);
                var scoreBadge = new Span(scorePct + "% similar");
                scoreBadge.addClassName("similarity-badge");
                simCard.getElement().appendChild(scoreBadge.getElement());
                content.add(simCard);
            }

            var details = new Details(summaryLayout, content);
            details.addClassName("cluster-details");
            propositionsContent.add(details);
        }

        // Unclustered propositions
        var unclustered = allPropositions.stream()
                .filter(p -> !clusteredIds.contains(p.getId()))
                .sorted(Comparator.comparing(Proposition::getCreated).reversed())
                .toList();

        if (!unclustered.isEmpty()) {
            var sectionHeader = new Span("Unclustered (" + unclustered.size() + ")");
            sectionHeader.addClassName("unclustered-section");
            propositionsContent.add(sectionHeader);

            for (var prop : unclustered) {
                propositionsContent.add(createCard(prop));
            }
        }
    }

    private PropositionCard createCard(Proposition prop) {
        var card = new PropositionCard(prop, entityResolver);
        if (onDelete != null) {
            card.setOnDelete(p -> {
                onDelete.accept(p.getId());
                refresh();
            });
        }
        return card;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
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
