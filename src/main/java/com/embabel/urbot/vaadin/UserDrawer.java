package com.embabel.urbot.vaadin;

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import com.embabel.urbot.rag.DocumentService;
import com.embabel.urbot.user.UrbotUser;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
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
    private final MemorySection memorySection;
    private final EntitiesSection entitiesSection;

    public UserDrawer(DocumentService documentService, UrbotUser user, Runnable onDocumentsChanged,
                      DrivinePropositionRepository propositionRepository,
                      Function<String, NamedEntity> entityResolver,
                      NamedEntityDataRepository entityRepository,
                      Runnable onAnalyze,
                      Consumer<MemorySection.RememberRequest> onRemember) {
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

        // Create documents section, memory section, and entities section early (referenced by context change listeners)
        documentsSection = new DocumentListSection(documentService,
                user::effectiveContext, onDocumentsChanged);
        memorySection = new MemorySection(propositionRepository, entityResolver,
                user::effectiveContext, onAnalyze, onRemember);
        entitiesSection = new EntitiesSection(entityRepository, user::effectiveContext);

        // Context selector section
        var contextSection = new HorizontalLayout();
        contextSection.setWidthFull();
        contextSection.setPadding(true);
        contextSection.setSpacing(true);
        contextSection.setAlignItems(VerticalLayout.Alignment.CENTER);

        var contextLabel = new Span("Context:");
        contextLabel.addClassName("context-label");

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
                memorySection.setContextId(user.effectiveContext());
                memorySection.refresh();
                entitiesSection.setContextId(user.effectiveContext());
            }
        });
        contextSelect.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                user.setCurrentContextName(e.getValue());
                documentsSection.refresh();
                onDocumentsChanged.run();
                memorySection.setContextId(user.effectiveContext());
                memorySection.refresh();
                entitiesSection.setContextId(user.effectiveContext());
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

        // Tabs - Memory, Entities, Documents, Upload, URL
        var memoryTab = new Tab(VaadinIcon.LIGHTBULB.create(), new Span("Memory"));
        var entitiesTab = new Tab(VaadinIcon.USER.create(), new Span("Entities"));
        var documentsTab = new Tab(VaadinIcon.FILE_TEXT.create(), new Span("Documents"));
        var uploadTab = new Tab(VaadinIcon.UPLOAD.create(), new Span("Upload"));
        var urlTab = new Tab(VaadinIcon.GLOBE.create(), new Span("URL"));

        var tabs = new Tabs(memoryTab, entitiesTab, documentsTab, uploadTab, urlTab);
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

        // Memory visible by default; others hidden
        entitiesSection.setVisible(false);
        documentsSection.setVisible(false);
        uploadSection.setVisible(false);
        urlSection.setVisible(false);

        contentArea.add(memorySection, entitiesSection, documentsSection, uploadSection, urlSection);
        sidePanel.add(contentArea);
        sidePanel.setFlexGrow(1, contentArea);

        // Tab switching
        tabs.addSelectedChangeListener(event -> {
            memorySection.setVisible(event.getSelectedTab() == memoryTab);
            entitiesSection.setVisible(event.getSelectedTab() == entitiesTab);
            documentsSection.setVisible(event.getSelectedTab() == documentsTab);
            uploadSection.setVisible(event.getSelectedTab() == uploadTab);
            urlSection.setVisible(event.getSelectedTab() == urlTab);
            if (event.getSelectedTab() == memoryTab) {
                memorySection.refresh();
            }
            if (event.getSelectedTab() == entitiesTab) {
                entitiesSection.refresh();
            }
            if (event.getSelectedTab() == documentsTab) {
                documentsSection.refresh();
            }
        });

        // Add elements (no toggle button - opened via user section click)
        getElement().appendChild(backdrop.getElement());
        getElement().appendChild(sidePanel.getElement());
    }

    public void open() {
        memorySection.refresh();
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
