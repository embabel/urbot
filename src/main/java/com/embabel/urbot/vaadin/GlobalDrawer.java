package com.embabel.urbot.vaadin;

import com.embabel.agent.core.DataDictionary;
import com.embabel.urbot.rag.DocumentService;
import com.embabel.urbot.user.UrbotUser;
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

    private final FileUploadSection uploadSection;
    private final UrlIngestSection urlSection;
    private final DocumentListSection documentsSection;
    private final SchemaSection schemaSection;

    public GlobalDrawer(DocumentService documentService, UrbotUser user, int neo4jHttpPort,
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
        var header = getHorizontalLayout(neo4jHttpPort);
        sidePanel.add(header);

        // Tabs - Documents first
        var documentsTab = new Tab(VaadinIcon.FILE_TEXT.create(), new Span("Documents"));
        var uploadTab = new Tab(VaadinIcon.UPLOAD.create(), new Span("Upload"));
        var urlTab = new Tab(VaadinIcon.GLOBE.create(), new Span("URL"));
        var schemaTab = new Tab(VaadinIcon.SITEMAP.create(), new Span("Schema"));

        var tabs = new Tabs(documentsTab, uploadTab, urlTab, schemaTab);
        tabs.setWidthFull();
        sidePanel.add(tabs);

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.addClassName("side-panel-content");
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        // Create sections using global context
        documentsSection = new DocumentListSection(documentService,
                () -> DocumentService.Context.GLOBAL_CONTEXT, onDocumentsChanged);

        uploadSection = new FileUploadSection(documentService, globalContext, () -> {
            documentsSection.refresh();
            onDocumentsChanged.run();
        });

        urlSection = new UrlIngestSection(documentService, globalContext, () -> {
            documentsSection.refresh();
            onDocumentsChanged.run();
        });

        schemaSection = new SchemaSection(dataDictionary);

        // Documents visible by default; others hidden
        uploadSection.setVisible(false);
        urlSection.setVisible(false);
        schemaSection.setVisible(false);

        contentArea.add(documentsSection, uploadSection, urlSection, schemaSection);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            var selected = event.getSelectedTab();
            documentsSection.setVisible(selected == documentsTab);
            uploadSection.setVisible(selected == uploadTab);
            urlSection.setVisible(selected == urlTab);
            schemaSection.setVisible(selected == schemaTab);
            if (selected == documentsTab) {
                documentsSection.refresh();
            }
        });

        // Add elements
        getElement().appendChild(backdrop.getElement());
        getElement().appendChild(toggleButton.getElement());
        getElement().appendChild(sidePanel.getElement());
    }

    private @NonNull HorizontalLayout getHorizontalLayout(int neo4jHttpPort) {
        var header = new HorizontalLayout();
        header.addClassName("side-panel-header");
        header.setWidthFull();

        var title = new Span(" Configuration");
        title.addClassName("side-panel-title");

        var neoLink = new Anchor("http://localhost:" + neo4jHttpPort, "Neo4j");
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
        documentsSection.refresh();
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
        documentsSection.refresh();
    }
}
