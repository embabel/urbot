package com.embabel.urbot.vaadin;

import com.embabel.agent.core.DataDictionary;
import com.embabel.urbot.rag.DocumentService;
import com.embabel.urbot.user.UrbotUser;
import com.embabel.vaadin.component.SchemaSection;
import com.embabel.vaadin.document.DocumentsPanel;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import org.jspecify.annotations.NonNull;

/**
 * Right-side drawer for global configuration and resources:
 * document upload, URL ingestion, document listing, and admin links.
 */
public class GlobalDrawer extends Div {

    private final VerticalLayout sidePanel;
    private final Div backdrop;
    private final Button toggleButton;
    private ShortcutRegistration escapeShortcut;

    private final DocumentsPanel documentsPanel;
    private final SchemaSection schemaSection;

    public GlobalDrawer(DocumentService documentService, UrbotUser user, int neo4jHttpPort, int neo4jBoltPort,
                        DataDictionary dataDictionary, Runnable onDocumentsChanged) {
        var globalContext = DocumentService.Context.global(user);

        // Backdrop for closing panel when clicking outside
        backdrop = new Div();
        backdrop.addClassName("side-panel-backdrop");
        backdrop.addClickListener(e -> close());

        // Toggle button on right edge
        toggleButton = new Button(VaadinIcon.ELLIPSIS_DOTS_V.create());
        toggleButton.addClassName("side-panel-toggle");
        toggleButton.getElement().setAttribute("title", "Global Config");
        toggleButton.addClickListener(e -> open());

        // Side panel
        sidePanel = new VerticalLayout();
        sidePanel.addClassName("side-panel");
        sidePanel.setPadding(false);
        sidePanel.setSpacing(false);

        // Header with close button
        var header = getHorizontalLayout(neo4jHttpPort, neo4jBoltPort);
        sidePanel.add(header);

        // Tabs
        var schemaTab = new Tab(VaadinIcon.SITEMAP.create(), new Span("Schema"));
        var documentsTab = new Tab(VaadinIcon.FILE_TEXT.create(), new Span("Documents"));
        var aboutTab = new Tab(VaadinIcon.INFO_CIRCLE.create(), new Span("About"));

        var tabs = new Tabs(schemaTab, documentsTab, aboutTab);
        tabs.setWidthFull();
        sidePanel.add(tabs);

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.addClassName("side-panel-content");
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        // Sections
        schemaSection = new SchemaSection(dataDictionary);

        documentsPanel = new DocumentsPanel(documentService,
                () -> DocumentService.Context.GLOBAL_CONTEXT,
                (is, fn) -> documentService.ingestStream(is, "upload://" + fn, fn, globalContext),
                url -> documentService.ingestUrl(url, globalContext),
                onDocumentsChanged);

        var aboutSection = new AboutSection();

        // Schema visible by default; others hidden
        documentsPanel.setVisible(false);
        aboutSection.setVisible(false);

        contentArea.add(schemaSection, documentsPanel, aboutSection);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            var selected = event.getSelectedTab();
            schemaSection.setVisible(selected == schemaTab);
            documentsPanel.setVisible(selected == documentsTab);
            aboutSection.setVisible(selected == aboutTab);
            if (selected == documentsTab) {
                documentsPanel.refresh();
            }
        });

        // Add elements
        getElement().appendChild(backdrop.getElement());
        getElement().appendChild(toggleButton.getElement());
        getElement().appendChild(sidePanel.getElement());
    }

    private @NonNull HorizontalLayout getHorizontalLayout(int neo4jHttpPort, int neo4jBoltPort) {
        var header = new HorizontalLayout();
        header.addClassName("side-panel-header");
        header.setWidthFull();

        var title = new Span(" Configuration");
        title.addClassName("side-panel-title");

        var neoLink = new Anchor(
                "http://localhost:" + neo4jHttpPort + "/browser/?connectURL=bolt://localhost:" + neo4jBoltPort,
                "Neo4j");
        neoLink.setTarget("_blank");
        neoLink.addClassName("side-panel-link");

        var closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addClassName("side-panel-close");
        closeButton.addClickListener(e -> close());

        header.add(title, neoLink, closeButton);
        header.setFlexGrow(1, title);
        return header;
    }

    public void open() {
        documentsPanel.refresh();
        sidePanel.addClassName("open");
        backdrop.addClassName("visible");
        toggleButton.addClassName("hidden");
        escapeShortcut = getUI().map(ui ->
                ui.addShortcutListener(this::close, Key.ESCAPE)
        ).orElse(null);
    }

    public void close() {
        sidePanel.removeClassName("open");
        backdrop.removeClassName("visible");
        toggleButton.removeClassName("hidden");
        if (escapeShortcut != null) {
            escapeShortcut.remove();
            escapeShortcut = null;
        }
    }

    public void refresh() {
        documentsPanel.refresh();
    }
}
