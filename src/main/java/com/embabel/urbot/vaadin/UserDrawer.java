package com.embabel.urbot.vaadin;

import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import com.embabel.urbot.rag.DocumentService;
import com.embabel.urbot.user.UrbotUser;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.ArrayList;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

/**
 * User drawer for personal document management and memory.
 * Opened by clicking the user profile chip. Contains context selector,
 * personal document upload/URL ingestion, document listing, and memory tab.
 */
public class UserDrawer extends Div {

    private final VerticalLayout sidePanel;
    private final Div backdrop;
    private ShortcutRegistration escapeShortcut;

    private final ComboBox<String> contextSelect;
    private final DocumentListSection documentsSection;
    private final DocumentService documentService;
    private final UrbotUser user;
    private final PropositionsPanel propositionsPanel;

    public UserDrawer(DocumentService documentService, UrbotUser user, Runnable onDocumentsChanged,
                      DrivinePropositionRepository propositionRepository, Runnable onAnalyze) {
        this.documentService = documentService;
        this.user = user;
        var personalContext = new DocumentService.Context(user);

        // Backdrop
        backdrop = new Div();
        backdrop.addClassName("side-panel-backdrop");
        backdrop.addClickListener(e -> close());

        // Side panel
        sidePanel = new VerticalLayout();
        sidePanel.addClassName("side-panel");
        sidePanel.setPadding(false);
        sidePanel.setSpacing(false);

        // Header with user info and close button
        var header = new HorizontalLayout();
        header.addClassName("side-panel-header");
        header.setWidthFull();

        var title = new Span(user.getDisplayName());
        title.addClassName("side-panel-title");

        var closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addClassName("side-panel-close");
        closeButton.addClickListener(e -> close());

        header.add(title, closeButton);
        header.setFlexGrow(1, title);
        sidePanel.add(header);

        // Create documents section and propositions panel early (referenced by context change listeners)
        documentsSection = new DocumentListSection(documentService,
                user::effectiveContext, onDocumentsChanged);
        propositionsPanel = new PropositionsPanel(propositionRepository);
        propositionsPanel.setContextId(user.effectiveContext());
        propositionsPanel.setOnDelete(id -> {
            propositionRepository.delete(id);
        });

        // Context selector section
        var contextSection = new HorizontalLayout();
        contextSection.setWidthFull();
        contextSection.setPadding(true);
        contextSection.setSpacing(true);
        contextSection.setAlignItems(VerticalLayout.Alignment.CENTER);

        var contextLabel = new Span("Context:");
        contextLabel.getStyle().set("color", "var(--sb-text-secondary)");
        contextLabel.getStyle().set("font-size", "var(--lumo-font-size-s)");

        contextSelect = new ComboBox<>();
        contextSelect.setAllowCustomValue(true);
        contextSelect.setPlaceholder("Context");
        contextSelect.addClassName("context-select");
        contextSelect.setWidthFull();
        contextSelect.addCustomValueSetListener(e -> {
            var newContext = e.getDetail().trim();
            if (!newContext.isEmpty()) {
                user.setCurrentContextName(newContext);
                contextSelect.setValue(newContext);
                documentsSection.refresh();
                onDocumentsChanged.run();
                propositionsPanel.setContextId(user.effectiveContext());
                propositionsPanel.refresh();
            }
        });
        contextSelect.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                user.setCurrentContextName(e.getValue());
                documentsSection.refresh();
                onDocumentsChanged.run();
                propositionsPanel.setContextId(user.effectiveContext());
                propositionsPanel.refresh();
            }
        });
        refreshContexts();

        var logoutButton = new Button("Logout", e -> {
            getUI().ifPresent(ui -> ui.getPage().setLocation("/logout"));
        });
        logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        logoutButton.addClassName("logout-button");

        contextSection.add(contextLabel, contextSelect, logoutButton);
        contextSection.setFlexGrow(1, contextSelect);
        sidePanel.add(contextSection);

        // Tabs - Documents, Upload, URL, Memory
        var documentsTab = new Tab(VaadinIcon.FILE_TEXT.create(), new Span("Documents"));
        var uploadTab = new Tab(VaadinIcon.UPLOAD.create(), new Span("Upload"));
        var urlTab = new Tab(VaadinIcon.GLOBE.create(), new Span("URL"));
        var memoryTab = new Tab(VaadinIcon.LIGHTBULB.create(), new Span("Memory"));

        var tabs = new Tabs(documentsTab, uploadTab, urlTab, memoryTab);
        tabs.setWidthFull();
        sidePanel.add(tabs);

        // Content area
        var contentArea = new VerticalLayout();
        contentArea.addClassName("side-panel-content");
        contentArea.setPadding(false);
        contentArea.setSizeFull();

        var uploadSection = new FileUploadSection(documentService, personalContext, () -> {
            documentsSection.refresh();
            onDocumentsChanged.run();
        });

        var urlSection = new UrlIngestSection(documentService, personalContext, () -> {
            documentsSection.refresh();
            onDocumentsChanged.run();
        });

        // Memory section
        var memoryButtonRow = new HorizontalLayout();
        memoryButtonRow.setWidthFull();
        memoryButtonRow.setSpacing(true);
        memoryButtonRow.addClassName("memory-button-row");

        var analyzeButton = new Button("Analyze", VaadinIcon.COG.create());
        analyzeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        analyzeButton.addClickListener(e -> {
            onAnalyze.run();
            // Schedule a refresh after extraction has time to complete
            getUI().ifPresent(ui -> propositionsPanel.scheduleRefresh(ui, 5000));
        });

        var clearAllButton = new Button("Clear All", VaadinIcon.TRASH.create());
        clearAllButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        clearAllButton.addClickListener(e -> {
            var dialog = new ConfirmDialog();
            dialog.setHeader("Clear All Memories");
            dialog.setText("Are you sure you want to delete all memories for this context? This cannot be undone.");
            dialog.setCancelable(true);
            dialog.setConfirmText("Clear All");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(event -> {
                propositionRepository.clearByContext(user.effectiveContext());
                propositionsPanel.refresh();
            });
            dialog.open();
        });

        memoryButtonRow.add(analyzeButton, clearAllButton);

        var memoryContent = new VerticalLayout();
        memoryContent.setPadding(true);
        memoryContent.setSpacing(true);
        memoryContent.setSizeFull();
        memoryContent.add(memoryButtonRow, propositionsPanel);
        memoryContent.setFlexGrow(1, propositionsPanel);

        // Documents visible by default; others hidden
        uploadSection.setVisible(false);
        urlSection.setVisible(false);
        memoryContent.setVisible(false);

        contentArea.add(documentsSection, uploadSection, urlSection, memoryContent);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            documentsSection.setVisible(event.getSelectedTab() == documentsTab);
            uploadSection.setVisible(event.getSelectedTab() == uploadTab);
            urlSection.setVisible(event.getSelectedTab() == urlTab);
            memoryContent.setVisible(event.getSelectedTab() == memoryTab);
            if (event.getSelectedTab() == documentsTab) {
                documentsSection.refresh();
            }
            if (event.getSelectedTab() == memoryTab) {
                propositionsPanel.refresh();
            }
        });

        // Add elements (no toggle button - opened via user section click)
        getElement().appendChild(backdrop.getElement());
        getElement().appendChild(sidePanel.getElement());
    }

    public void open() {
        documentsSection.refresh();
        refreshContexts();
        sidePanel.addClassName("open");
        backdrop.addClassName("visible");
        escapeShortcut = getUI().map(ui ->
                ui.addShortcutListener(this::close, Key.ESCAPE)
        ).orElse(null);
    }

    public void close() {
        sidePanel.removeClassName("open");
        backdrop.removeClassName("visible");
        if (escapeShortcut != null) {
            escapeShortcut.remove();
            escapeShortcut = null;
        }
    }

    public void refreshContexts() {
        var prefix = user.getId() + "_";
        var contextNames = new ArrayList<>(
                documentService.contexts().stream()
                        .filter(ctx -> ctx.startsWith(prefix))
                        .map(ctx -> ctx.substring(prefix.length()))
                        .distinct()
                        .toList()
        );
        if (!contextNames.contains(user.getCurrentContextName())) {
            contextNames.add(0, user.getCurrentContextName());
        }
        contextSelect.setItems(contextNames);
        contextSelect.setValue(user.getCurrentContextName());
    }
}
