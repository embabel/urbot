package com.embabel.urbot.rag;

import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.model.NavigableDocument;
import com.embabel.agent.rag.store.ChunkingContentElementRepository;
import com.embabel.urbot.user.UrbotUser;
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

    public record Context(UrbotUser user) {

        public static final String CONTEXT_KEY = "context";

        public static final String GLOBAL_CONTEXT = "global";

        public Map<String, Object> metadata() {
            return Map.of(
                    "ingestedBy", user.getId(),
                    CONTEXT_KEY, user.effectiveContext()
            );
        }
    }

    public DocumentService(ChunkingContentElementRepository contentRepository) {
        this.contentRepository = contentRepository;
        this.contentReader = new TikaHierarchicalContentReader();
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
                context.user().effectiveContext(),
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
            documents.removeIf(doc -> doc.uri().equals(uri));
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
        return (int) documents.stream()
                .filter(doc -> doc.context().equals(effectiveContext))
                .count();
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
        return documents.stream()
                .filter(doc -> doc.context().equals(effectiveContext))
                .mapToInt(DocumentInfo::chunkCount)
                .sum();
    }

}
