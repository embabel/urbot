package com.embabel.urbot.proposition.extraction;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.chat.Message;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.KnownEntity;
import com.embabel.dice.common.Relations;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.common.resolver.KnownEntityResolver;
import com.embabel.dice.incremental.*;
import com.embabel.dice.incremental.proposition.PropositionIncrementalAnalyzer;
import com.embabel.dice.pipeline.ChunkPropositionResult;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.projection.graph.GraphProjector;
import com.embabel.dice.projection.graph.GraphRelationshipPersister;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.ReferencesEntities;
import com.embabel.urbot.UrbotProperties;
import com.embabel.urbot.event.ConversationAnalysisRequestEvent;
import com.embabel.urbot.user.UrbotUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Async listener that extracts propositions from chat conversations.
 * Uses IncrementalAnalyzer for windowed, deduplicated processing.
 */
@Service
public class ConversationPropositionExtraction {

    private static final Logger logger = LoggerFactory.getLogger(ConversationPropositionExtraction.class);

    private final IncrementalAnalyzer<Message, ChunkPropositionResult> analyzer;
    private final DataDictionary dataDictionary;
    private final Relations relations;
    private final PropositionRepository propositionRepository;
    private final NamedEntityDataRepository entityRepository;
    private final EntityResolver entityResolver;
    private final GraphProjector graphProjector;
    private final GraphRelationshipPersister graphRelationshipPersister;

    public ConversationPropositionExtraction(
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
        this.dataDictionary = dataDictionary;
        this.relations = relations;
        this.propositionRepository = propositionRepository;
        this.entityRepository = entityRepository;
        this.entityResolver = entityResolver;
        this.graphProjector = graphProjector;
        this.graphRelationshipPersister = graphRelationshipPersister;

        var extraction = properties.extraction();
        var config = new WindowConfig(
                extraction.windowSize(),
                extraction.overlapSize(),
                extraction.triggerInterval()
        );
        this.analyzer = new PropositionIncrementalAnalyzer<>(
                propositionPipeline,
                chunkHistoryStore,
                MessageFormatter.INSTANCE,
                config
        );
    }

    @Async
    @Transactional
    @EventListener
    public void onConversationExchange(ConversationAnalysisRequestEvent event) {
        extractPropositions(event);
    }

    private EntityResolver entityResolverForUser(UrbotUser user) {
        return KnownEntityResolver.withKnownEntities(
                java.util.List.of(KnownEntity.asCurrentUser(user)),
                entityResolver
        );
    }

    public void extractPropositions(ConversationAnalysisRequestEvent event) {
        logger.debug("Received request for proposition extraction for conversation with {} messages",
                event.conversation.getMessages().size());
        try {
            var messages = event.conversation.getMessages();
            if (messages.size() < 2) {
                logger.info("Not enough messages for extraction (need at least 2)");
                return;
            }

            var context = SourceAnalysisContext
                    .withContextId(event.user.currentContext())
                    .withEntityResolver(entityResolverForUser(event.user))
                    .withSchema(dataDictionary)
                    .withRelations(relations)
                    .withKnownEntities(
                            KnownEntity.asCurrentUser(event.user)
                    )
                    .withPromptVariables(Map.of(
                            "user", event.user
                    ));

            logger.info("Context relations count: {}, injected relations count: {}",
                    context.getRelations().size(), relations.size());

            var source = new ConversationSource(event.conversation);
            var result = analyzer.analyze(source, context);

            if (result == null) {
                logger.info("Analysis skipped (not ready or already processed)");
                return;
            }

            if (result.getPropositions().isEmpty()) {
                logger.info("Analysis completed but no propositions extracted");
                return;
            }
            var resolvedCount = result.getPropositions().stream()
                    .filter(ReferencesEntities::isFullyResolved)
                    .count();

            logger.info(result.infoString(true, 1));

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
        } catch (Exception e) {
            logger.warn("Failed to extract propositions", e);
        }
    }
}
