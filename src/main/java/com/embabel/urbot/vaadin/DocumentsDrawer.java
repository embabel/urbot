package com.embabel.urbot.vaadin;

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

/**
 * Right-side drawer for global document management: upload, URL ingestion, and document listing.
 * Documents ingested here use the global context.
 */
public class DocumentsDrawer extends Div {

    private final VerticalLayout sidePanel;
    private final Div backdrop;
    private final Button toggleButton;
    private ShortcutRegistration escapeShortcut;

    private final FileUploadSection uploadSection;
    private final UrlIngestSection urlSection;
    private final DocumentListSection documentsSection;

    public DocumentsDrawer(DocumentService documentService, UrbotUser user, int neo4jHttpPort, Runnable onDocumentsChanged) {
        var globalContext = DocumentService.Context.global(user);

        // Backdrop for closing panel when clicking outside
        backdrop = new Div();
        backdrop.addClassName("side-panel-backdrop");
        backdrop.addClickListener(e -> close());

        // Toggle button on right edge
        toggleButton = new Button(VaadinIcon.ELLIPSIS_DOTS_V.create());
        toggleButton.addClassName("side-panel-toggle");
        toggleButton.getElement().setAttribute("title", "Global Documents");
        toggleButton.addClickListener(e -> open());

        // Side panel
        sidePanel = new VerticalLayout();
        sidePanel.addClassName("side-panel");
        sidePanel.setPadding(false);
        sidePanel.setSpacing(false);

        // Header with close button
        var header = new HorizontalLayout();
        header.addClassName("side-panel-header");
        header.setWidthFull();

        var title = new Span("Global Documents");
        title.addClassName("side-panel-title");

        var neoLink = new Anchor("http://localhost:" + neo4jHttpPort, "Neo4j");
        neoLink.setTarget("_blank");
        neoLink.addClassName("side-panel-link");

        var closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addClassName("side-panel-close");
        closeButton.addClickListener(e -> close());

        header.add(title, neoLink, closeButton);
        header.setFlexGrow(1, title);
        sidePanel.add(header);

        // Tabs - Documents first
        var documentsTab = new Tab(VaadinIcon.FILE_TEXT.create(), new Span("Documents"));
        var uploadTab = new Tab(VaadinIcon.UPLOAD.create(), new Span("Upload"));
        var urlTab = new Tab(VaadinIcon.GLOBE.create(), new Span("URL"));

        var tabs = new Tabs(documentsTab, uploadTab, urlTab);
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

        // Documents visible by default; others hidden
        uploadSection.setVisible(false);
        urlSection.setVisible(false);

        contentArea.add(documentsSection, uploadSection, urlSection);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            documentsSection.setVisible(event.getSelectedTab() == documentsTab);
            uploadSection.setVisible(event.getSelectedTab() == uploadTab);
            urlSection.setVisible(event.getSelectedTab() == urlTab);
            if (event.getSelectedTab() == documentsTab) {
                documentsSection.refresh();
            }
        });

        // Add elements
        getElement().appendChild(backdrop.getElement());
        getElement().appendChild(toggleButton.getElement());
        getElement().appendChild(sidePanel.getElement());
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
