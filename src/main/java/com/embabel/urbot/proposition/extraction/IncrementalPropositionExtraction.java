package com.embabel.urbot.proposition.extraction;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.ingestion.TikaHierarchicalContentReader;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.chat.Message;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.KnownEntity;
import com.embabel.dice.common.Relations;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.common.resolver.KnownEntityResolver;
import com.embabel.dice.incremental.ChunkHistoryStore;
import com.embabel.dice.incremental.IncrementalAnalyzer;
import com.embabel.dice.incremental.MessageFormatter;
import com.embabel.dice.incremental.WindowConfig;
import com.embabel.dice.incremental.proposition.PropositionIncrementalAnalyzer;
import com.embabel.dice.pipeline.ChunkPropositionResult;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.projection.graph.GraphProjector;
import com.embabel.dice.projection.graph.GraphRelationshipPersister;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.urbot.UrbotProperties;
import com.embabel.urbot.event.SourceAnalysisRequestEvent;
import com.embabel.urbot.user.UrbotUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Async listener that extracts propositions from any incremental source
 * (conversations, message streams, etc.).
 * Uses IncrementalAnalyzer for windowed, deduplicated processing.
 */
@Service
public class IncrementalPropositionExtraction {

    private static final Logger logger = LoggerFactory.getLogger(IncrementalPropositionExtraction.class);

    private final IncrementalAnalyzer<Message, ChunkPropositionResult> analyzer;
    private final PropositionPipeline pipeline;
    private final ChunkHistoryStore chunkHistoryStore;
    private final WindowConfig windowConfig;
    private final DataDictionary dataDictionary;
    private final Relations relations;
    private final PropositionRepository propositionRepository;
    private final NamedEntityDataRepository entityRepository;
    private final EntityResolver entityResolver;
    private final GraphProjector graphProjector;
    private final GraphRelationshipPersister graphRelationshipPersister;

    public IncrementalPropositionExtraction(
            PropositionPipeline propositionPipeline,
            ChunkHistoryStore chunkHistoryStore,
            DataDictionary dataDictionary,
            Relations relations,
            PropositionRepository propositionRepository,
            NamedEntityDataRepository entityRepository,
            EntityResolver entityResolver,
            GraphProjector graphProjector,
            GraphRelationshipPersister graphRelationshipPersister,
            UrbotProperties properties) {
        this.pipeline = propositionPipeline;
        this.chunkHistoryStore = chunkHistoryStore;
        this.dataDictionary = dataDictionary;
        this.relations = relations;
        this.propositionRepository = propositionRepository;
        this.entityRepository = entityRepository;
        this.entityResolver = entityResolver;
        this.graphProjector = graphProjector;
        this.graphRelationshipPersister = graphRelationshipPersister;

        var extraction = properties.memory();
        this.windowConfig = new WindowConfig(
                extraction.windowSize(),
                extraction.overlapSize(),
                extraction.triggerInterval()
        );
        this.analyzer = new PropositionIncrementalAnalyzer<>(
                propositionPipeline,
                chunkHistoryStore,
                MessageFormatter.INSTANCE,
                windowConfig
        );
    }

    @Async
    @Transactional
    @EventListener
    public void onSourceAnalysisRequestEvent(SourceAnalysisRequestEvent event) {
        extractPropositions(event);
    }

    private EntityResolver entityResolverForUser(UrbotUser user) {
        return KnownEntityResolver.withKnownEntities(
                java.util.List.of(KnownEntity.asCurrentUser(user)),
                entityResolver
        );
    }

    public void extractPropositions(SourceAnalysisRequestEvent event) {
        try {
            var source = event.incrementalSource();
            if (source.getSize() < windowConfig.getOverlapSize()) {
                logger.info("Source {} has {} items, need at least {} for extraction",
                        source.getId(), source.getSize(), windowConfig.getOverlapSize());
                return;
            }

            var context = buildContext(event.user);

            logger.info("Context relations count: {}, injected relations count: {}",
                    context.getRelations().size(), relations.size());

            var result = analyzer.analyze(source, context);

            if (result == null) {
                logger.info("Analysis skipped (not ready or already processed)");
                return;
            }

            if (result.getPropositions().isEmpty()) {
                logger.info("Analysis completed but no propositions extracted");
                return;
            }

            logger.info(result.infoString(true, 1));
            persistAndProject(result);
        } catch (Exception e) {
            logger.warn("Failed to extract propositions", e);
        }
    }

    public void rememberFile(InputStream inputStream, String filename, UrbotUser user) {
        try {
            var reader = new TikaHierarchicalContentReader();
            var document = reader.parseContent(inputStream, "remember://" + filename);

            var sb = new StringBuilder();
            for (var leaf : document.leaves()) {
                sb.append(leaf.getText()).append("\n\n");
            }
            var text = sb.toString().trim();
            if (text.isEmpty()) {
                logger.info("No text extracted from file: {}", filename);
                return;
            }

            var context = buildContext(user);
            var sourceId = "remember:" + filename;
            ChunkPropositionResult result = pipeline.processOnce(text, sourceId, context);

            if (!result.getPropositions().isEmpty()) {
                logger.info(result.infoString(true, 1));
                persistAndProject(result);
                logger.info("Remembered file: {}", filename);
            } else {
                logger.info("No propositions extracted from file: {}", filename);
            }
        } catch (Exception e) {
            logger.warn("Failed to remember file: {}", filename, e);
        }
    }

    private SourceAnalysisContext buildContext(UrbotUser user) {
        return SourceAnalysisContext
                .withContextId(user.currentContext())
                .withEntityResolver(entityResolverForUser(user))
                .withSchema(dataDictionary)
                .withRelations(relations)
                .withKnownEntities(
                        KnownEntity.asCurrentUser(user)
                )
                .withPromptVariables(Map.of(
                        "user", user
                ));
    }

    private void persistAndProject(ChunkPropositionResult result) {
        var propsToSave = result.propositionsToPersist();
        var referencedEntityIds = propsToSave.stream()
                .flatMap(p -> p.getMentions().stream())
                .map(EntityMention::getResolvedId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var newEntitiesToSave = result.newEntities().stream()
                .filter(e -> referencedEntityIds.contains(e.getId()))
                .count();

        var stats = result.getPropositionExtractionStats();
        var newProps = stats.getNewCount();
        var updatedProps = stats.getMergedCount() + stats.getReinforcedCount();

        result.persist(propositionRepository, entityRepository);
        if (newProps > 0 || updatedProps > 0 || newEntitiesToSave > 0) {
            logger.info("Persisted: {} new propositions, {} updated propositions, {} new entities",
                    newProps, updatedProps, newEntitiesToSave);
        } else {
            logger.info("No new data to persist (all propositions were duplicates)");
        }

        var projectionResults = graphProjector.projectAll(propsToSave, dataDictionary);
        if (!projectionResults.getProjected().isEmpty()) {
            var persistenceResult = graphRelationshipPersister.persist(projectionResults);
            logger.info("Projected {} semantic relationships from propositions",
                    persistenceResult.getPersistedCount());
        }
    }
}
