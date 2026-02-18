package com.embabel.urbot.vaadin;

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.urbot.proposition.persistence.DrivinePropositionRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.function.Consumer;
import java.util.function.Function;
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
            Function<String, NamedEntity> entityResolver,
            Supplier<String> contextIdSupplier,
            Runnable onAnalyze,
            Consumer<RememberRequest> onRemember) {
        this.propositionRepository = propositionRepository;
        this.contextIdSupplier = contextIdSupplier;

        // Create propositions panel early (referenced by button listeners)
        propositionsPanel = new PropositionsPanel(propositionRepository, entityResolver);
        propositionsPanel.setContextId(contextIdSupplier.get());
        propositionsPanel.setOnDelete(id -> propositionRepository.delete(id));

        setPadding(true);
        setSpacing(true);
        setSizeFull();

        // Button row
        var buttonRow = new HorizontalLayout();
        buttonRow.setWidthFull();
        buttonRow.setSpacing(true);
        buttonRow.addClassName("memory-button-row");

        // Upload status row — shown below buttons during upload/extraction
        var statusRow = new HorizontalLayout();
        statusRow.setWidthFull();
        statusRow.setAlignItems(Alignment.CENTER);
        statusRow.setSpacing(true);
        statusRow.setPadding(false);
        statusRow.addClassName("learn-status-row");
        statusRow.setVisible(false);

        var statusLabel = new Span();
        statusLabel.addClassName("learn-status-label");

        var statusBar = new ProgressBar();
        statusBar.addClassName("learn-progress-bar");

        statusRow.add(statusLabel, statusBar);
        statusRow.setFlexGrow(1, statusBar);

        // "Learn" file upload — progress shown in status row below
        if (onRemember != null) {
            var buffer = new MemoryBuffer();
            var upload = new Upload(buffer);
            upload.setDropAllowed(false);
            var learnButton = new Button("Learn", VaadinIcon.BOOK.create());
            upload.setUploadButton(learnButton);
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
            upload.setMaxFiles(1);
            upload.addClassName("learn-upload");

            // Immediately clear the file list so it never shows inline
            upload.getElement().addEventListener("upload-start", e ->
                    upload.getElement().executeJs("this.files = []"));
            upload.getElement().addEventListener("upload-success", e ->
                    upload.getElement().executeJs("this.files = []"));

            upload.addStartedListener(event -> {
                statusLabel.setText("Uploading: " + event.getFileName());
                statusBar.setIndeterminate(false);
                statusBar.setValue(0);
                statusRow.setVisible(true);
            });

            upload.addProgressListener(event -> {
                if (event.getContentLength() > 0) {
                    statusBar.setIndeterminate(false);
                    statusBar.setValue((double) event.getReadBytes() / event.getContentLength());
                } else {
                    statusBar.setIndeterminate(true);
                }
            });

            upload.addSucceededListener(event -> {
                var filename = event.getFileName();
                statusLabel.setText("Extracting memories from: " + filename);
                statusBar.setIndeterminate(true);
                try {
                    onRemember.accept(new RememberRequest(buffer.getInputStream(), filename));
                    // Hide status after extraction has had time to start
                    getUI().ifPresent(ui -> {
                        propositionsPanel.scheduleRefresh(ui, 5000);
                        new Thread(() -> {
                            try {
                                Thread.sleep(5000);
                                ui.access(() -> statusRow.setVisible(false));
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    });
                } catch (Exception e) {
                    logger.error("Failed to remember file: {}", filename, e);
                    statusLabel.setText("Error: " + e.getMessage());
                    statusBar.setVisible(false);
                }
            });

            upload.addFailedListener(event -> {
                logger.error("Upload failed: {}", event.getReason().getMessage());
                statusLabel.setText("Upload failed");
                statusRow.setVisible(false);
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

        add(buttonRow, statusRow, propositionsPanel);
        setFlexGrow(1, propositionsPanel);
    }

    public void refresh() {
        propositionsPanel.refresh();
    }

    public void setContextId(String contextId) {
        propositionsPanel.setContextId(contextId);
    }
}
