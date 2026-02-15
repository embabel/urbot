package com.embabel.urbot.vaadin;

import com.embabel.dice.proposition.EntityMention;
import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reusable memory section with file upload ("Remember"), analyze, clear all,
 * and propositions display. Parameterized by context so it can be used for
 * user-specific, global, or bot-specific memories.
 */
public class MemorySection extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(MemorySection.class);

    private final DrivinePropositionRepository propositionRepository;
    private final Supplier<String> contextIdSupplier;
    private final PropositionsPanel propositionsPanel;

    public record RememberRequest(InputStream inputStream, String filename) {}

    public MemorySection(
            DrivinePropositionRepository propositionRepository,
            Supplier<String> contextIdSupplier,
            Runnable onAnalyze,
            Consumer<RememberRequest> onRemember) {
        this.propositionRepository = propositionRepository;
        this.contextIdSupplier = contextIdSupplier;

        // Create propositions panel early (referenced by button listeners)
        propositionsPanel = new PropositionsPanel(propositionRepository);
        propositionsPanel.setContextId(contextIdSupplier.get());
        propositionsPanel.setOnDelete(id -> propositionRepository.delete(id));
        propositionsPanel.setOnMentionClick(mention -> {
            var dialog = new ConfirmDialog();
            dialog.setHeader(mention.getSpan());
            var details = new StringBuilder();
            details.append("Type: ").append(mention.getType()).append("\n");
            if (mention.getResolvedId() != null) {
                details.append("ID: ").append(mention.getResolvedId()).append("\n");
            }
            if (mention.getRole() != null) {
                details.append("Role: ").append(mention.getRole()).append("\n");
            }
            dialog.setText(details.toString());
            dialog.setConfirmText("OK");
            dialog.setCancelable(false);
            dialog.open();
        });

        setPadding(true);
        setSpacing(true);
        setSizeFull();

        // Button row
        var buttonRow = new HorizontalLayout();
        buttonRow.setWidthFull();
        buttonRow.setSpacing(true);
        buttonRow.addClassName("memory-button-row");

        // "Remember" file upload
        if (onRemember != null) {
            var buffer = new MemoryBuffer();
            var upload = new Upload(buffer);
            upload.setDropAllowed(false);
            upload.setUploadButton(new Button("Remember", VaadinIcon.BOOK.create()));
            upload.setAcceptedFileTypes(
                    ".pdf", ".txt", ".md", ".html", ".htm",
                    ".doc", ".docx", ".odt", ".rtf",
                    "application/pdf",
                    "text/plain",
                    "text/markdown",
                    "text/html",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            );
            upload.setMaxFileSize(10 * 1024 * 1024); // 10MB

            upload.addSucceededListener(event -> {
                var filename = event.getFileName();
                try {
                    onRemember.accept(new RememberRequest(buffer.getInputStream(), filename));
                    Notification.show("Remembering: " + filename, 3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    // Schedule a refresh after extraction has time to complete
                    getUI().ifPresent(ui -> propositionsPanel.scheduleRefresh(ui, 5000));
                } catch (Exception e) {
                    logger.error("Failed to remember file: {}", filename, e);
                    Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });

            upload.addFailedListener(event -> {
                logger.error("Upload failed: {}", event.getReason().getMessage());
                Notification.show("Upload failed: " + event.getReason().getMessage(),
                        5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            });

            buttonRow.add(upload);
        }

        // "Analyze" button (optional)
        if (onAnalyze != null) {
            var analyzeButton = new Button("Analyze", VaadinIcon.COG.create());
            analyzeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            analyzeButton.addClickListener(e -> {
                onAnalyze.run();
                getUI().ifPresent(ui -> propositionsPanel.scheduleRefresh(ui, 5000));
            });
            buttonRow.add(analyzeButton);
        }

        // "Clear All" button
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
                propositionRepository.clearByContext(contextIdSupplier.get());
                propositionsPanel.refresh();
            });
            dialog.open();
        });
        buttonRow.add(clearAllButton);

        add(buttonRow, propositionsPanel);
        setFlexGrow(1, propositionsPanel);
    }

    public void refresh() {
        propositionsPanel.refresh();
    }

    public void setContextId(String contextId) {
        propositionsPanel.setContextId(contextId);
    }
}
