package com.embabel.urbot.rag;

import com.embabel.agent.filter.PropertyFilter;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.model.ContentRoot;
import com.embabel.agent.rag.model.NavigableDocument;
import com.embabel.agent.rag.store.ChunkingContentElementRepository;
import com.embabel.urbot.user.UrbotUser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing document ingestion and retrieval.
 */
@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private final ChunkingContentElementRepository contentRepository;
    private final TikaHierarchicalContentReader contentReader;
    private final List<DocumentInfo> documents = new CopyOnWriteArrayList<>();

    /**
     * Summary info about an ingested document.
     */
    public record DocumentInfo(String uri, String title, String context, int chunkCount, Instant ingestedAt) {
    }

    public record Context(UrbotUser user, String overrideContext) {

        public static final String CONTEXT_KEY = "context";

        public static final String GLOBAL_CONTEXT = "global";

        public Context(UrbotUser user) {
            this(user, null);
        }

        public static Context global(UrbotUser user) {
            return new Context(user, GLOBAL_CONTEXT);
        }

        public String effectiveContext() {
            return overrideContext != null ? overrideContext : user.effectiveContext();
        }

        public Map<String, Object> metadata() {
            return Map.of(
                    "ingestedBy", user.getId(),
                    CONTEXT_KEY, effectiveContext()
            );
        }
    }

    public DocumentService(ChunkingContentElementRepository contentRepository) {
        this.contentRepository = contentRepository;
        this.contentReader = new TikaHierarchicalContentReader();
    }

    @PostConstruct
    void loadDocumentsFromDatabase() {
        try {
            for (var root : contentRepository.findAll(ContentRoot.class)) {
                var context = (String) root.getMetadata().get(Context.CONTEXT_KEY);
                if (context == null) continue;
                documents.add(new DocumentInfo(
                        root.getUri(),
                        root.getTitle(),
                        context,
                        0,
                        root.getIngestionTimestamp()
                ));
            }
            logger.info("Loaded {} documents from database", documents.size());
        } catch (Exception e) {
            logger.warn("Failed to load documents from database: {}", e.getMessage());
        }
    }

    /**
     * Ingest a file into the RAG store.
     */
    public NavigableDocument ingestFile(File file, Context context) {
        logger.info("Ingesting file: {}", file.getName());
        var document = contentReader.parseFile(file, file.toURI().toString())
                .withMetadata(context.metadata());
        var chunkIds = contentRepository.writeAndChunkDocument(document);
        trackDocument(document, context, chunkIds.size());
        logger.info("Ingested file: {}", file.getName());
        return document;
    }

    /**
     * Ingest content from an input stream.
     */
    public NavigableDocument ingestStream(InputStream inputStream, String uri, String filename, Context context) {
        logger.info("Ingesting stream: {}", filename);
        var document = contentReader.parseContent(inputStream, uri)
                .withMetadata(context.metadata());
        var chunkIds = contentRepository.writeAndChunkDocument(document);
        trackDocument(document, context, chunkIds.size());
        logger.info("Ingested: {}", filename);
        return document;
    }

    /**
     * Ingest content from a URL.
     */
    public NavigableDocument ingestUrl(String url, Context context) {
        logger.info("Ingesting URL: {}", url);
        var document = contentReader.parseResource(url)
                .withMetadata(context.metadata());
        var chunkIds = contentRepository.writeAndChunkDocument(document);
        trackDocument(document, context, chunkIds.size());
        logger.info("Ingested URL: {}", url);
        return document;
    }

    private void trackDocument(NavigableDocument document, Context context, int chunkCount) {
        documents.add(new DocumentInfo(
                document.getUri(),
                document.getTitle(),
                context.effectiveContext(),
                chunkCount,
                Instant.now()
        ));
    }

    /**
     * Get list of all ingested documents.
     */
    public List<DocumentInfo> getDocuments() {
        return List.copyOf(documents);
    }

    /**
     * Get documents filtered by the user's effective context.
     */
    public List<DocumentInfo> getDocuments(String effectiveContext) {
        return documents.stream()
                .filter(doc -> doc.context().equals(effectiveContext))
                .toList();
    }

    /**
     * Get list of distinct contexts found in documents
     */
    public List<String> contexts() {
        return documents.stream()
                .map(DocumentInfo::context)
                .distinct()
                .toList();
    }

    /**
     * Delete a document by its URI.
     */
    public boolean deleteDocument(String uri) {
        logger.info("Deleting document: {}", uri);
        var result = contentRepository.deleteRootAndDescendants(uri);
        if (result != null) {
            documents.stream()
                    .filter(doc -> doc.uri().equals(uri))
                    .findFirst()
                    .ifPresent(documents::remove);
            return true;
        }
        return false;
    }

    /**
     * Get total document count.
     */
    public int getDocumentCount() {
        return contentRepository.info().getDocumentCount();
    }

    /**
     * Get document count for a specific context.
     */
    public int getDocumentCount(String effectiveContext) {
        return contentRepository.count(ContentRoot.class,
                new PropertyFilter.Eq(Context.CONTEXT_KEY, effectiveContext));
    }

    /**
     * Get total chunk count.
     */
    public int getChunkCount() {
        return contentRepository.info().getChunkCount();
    }

    /**
     * Get chunk count for a specific context.
     */
    public int getChunkCount(String effectiveContext) {
        return contentRepository.count(Chunk.class,
                new PropertyFilter.Eq(Context.CONTEXT_KEY, effectiveContext));
    }

}
