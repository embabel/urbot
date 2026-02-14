package com.embabel.urbot.proposition;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.agent.rag.neo.drivine.DrivineNamedEntityDataRepository;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.dice.common.*;
import com.embabel.dice.common.resolver.BakeoffPromptStrategies;
import com.embabel.dice.common.resolver.EscalatingEntityResolver;
import com.embabel.dice.common.resolver.LlmCandidateBakeoff;
import com.embabel.dice.common.support.InMemorySchemaRegistry;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.projection.graph.GraphProjector;
import com.embabel.dice.projection.graph.GraphRelationshipPersister;
import com.embabel.dice.projection.graph.NamedEntityDataRepositoryGraphRelationshipPersister;
import com.embabel.dice.projection.graph.RelationBasedGraphProjector;
import com.embabel.dice.projection.memory.MemoryProjector;
import com.embabel.dice.projection.memory.support.DefaultMemoryProjector;
import com.embabel.dice.projection.memory.support.RelationBasedKnowledgeTypeClassifier;
import com.embabel.dice.proposition.PropositionExtractor;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.extraction.LlmPropositionExtractor;
import com.embabel.dice.proposition.revision.LlmPropositionReviser;
import com.embabel.dice.proposition.revision.PropositionReviser;
import com.embabel.urbot.UrbotProperties;
import com.embabel.urbot.user.UrbotUser;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for proposition extraction from chat conversations.
 * Sets up the DICE pipeline components for extracting and storing propositions.
 */
@Configuration
@EnableAsync
class PropositionConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PropositionConfiguration.class);

    @Bean
    @Primary
    DataDictionary urbotSchema(UrbotProperties properties) {
        var packages = properties.propositionExtraction().entityPackages();
        var schema = DataDictionary.fromClasses("urbot", UrbotUser.class)
                .plus(NamedEntity.dataDictionaryFromPackages(
                        packages.toArray(String[]::new)
                ));
        logger.info("Created urbot domain schema with {} types from packages {}",
                schema.getDomainTypes().size(), packages);
        return schema;
    }

    @Bean
    SchemaRegistry schemaRegistry(DataDictionary urbotSchema) {
        var registry = new InMemorySchemaRegistry(urbotSchema);
        registry.register(urbotSchema);
        logger.info("Created SchemaRegistry with default urbot schema");
        return registry;
    }

    @Bean
    Relations relations() {
        return Relations.empty()
                .withPredicatesForSubject(
                        UrbotUser.class, KnowledgeType.SEMANTIC,
                        "likes", "dislikes", "knows", "is_interested_in", "works_on", "prefers"
                )
                .withSemanticBetween("UrbotUser", "Pet", "owns", "user owns a pet")
                .withSemanticBetween("UrbotUser", "Company", "works_at", "user works at a company")
                .withSemanticBetween("UrbotUser", "Goal", "is_working_toward", "user is working toward a goal");
    }

    @Bean
    GraphProjector graphProjector(Relations relations) {
        return RelationBasedGraphProjector.from(relations);
    }

    @Bean
    GraphRelationshipPersister graphRelationshipPersister(NamedEntityDataRepository repository) {
        return new NamedEntityDataRepositoryGraphRelationshipPersister(repository);
    }

    @Bean
    LlmPropositionExtractor llmPropositionExtractor(
            AiBuilder aiBuilder,
            PropositionRepository propositionRepository,
            UrbotProperties properties) {
        var extraction = properties.propositionExtraction();
        var ai = aiBuilder
                .withShowPrompts(extraction.showPrompts())
                .withShowLlmResponses(extraction.showResponses())
                .ai();
        logger.info("Creating LlmPropositionExtractor with model: {}", extraction.extractionLlm());
        return LlmPropositionExtractor
                .withLlm(extraction.extractionLlm())
                .withAi(ai)
                .withPropositionRepository(propositionRepository)
                .withSchemaAdherence(SchemaAdherence.DEFAULT)
                .withTemplate("dice/extract_urbot_user_propositions");
    }

    @Bean
    NamedEntityDataRepository namedEntityDataRepository(
            PersistenceManager persistenceManager,
            EmbeddingService embeddingService,
            GraphObjectManager graphObjectManager,
            DataDictionary dataDictionary,
            UrbotProperties properties) {
        return new DrivineNamedEntityDataRepository(
                persistenceManager,
                properties.neoRag(),
                dataDictionary,
                embeddingService,
                graphObjectManager
        );
    }

    @Bean
    EntityResolver entityResolver(
            NamedEntityDataRepository repository,
            AiBuilder aiBuilder,
            UrbotProperties properties) {
        var extraction = properties.propositionExtraction();
        var llmOptions = extraction.entityResolutionLlm();
        var ai = aiBuilder
                .withShowPrompts(extraction.showPrompts())
                .withShowLlmResponses(extraction.showResponses())
                .ai();

        var llmBakeoff = LlmCandidateBakeoff
                .withLlm(llmOptions)
                .withAi(ai)
                .withPromptStrategy(BakeoffPromptStrategies.FULL);

        logger.info("Creating EscalatingEntityResolver with model: {}", llmOptions.getModel());
        return EscalatingEntityResolver.create(repository, llmBakeoff);
    }

    @Bean
    PropositionPipeline propositionPipeline(
            PropositionExtractor propositionExtractor,
            PropositionReviser propositionReviser,
            PropositionRepository propositionRepository) {
        logger.info("Building proposition extraction pipeline");
        return PropositionPipeline
                .withExtractor(propositionExtractor)
                .withRevision(propositionReviser, propositionRepository);
    }

    @Bean
    PropositionReviser propositionReviser(
            AiBuilder aiBuilder,
            UrbotProperties properties) {
        var extraction = properties.propositionExtraction();
        var ai = aiBuilder
                .withShowPrompts(extraction.showPrompts())
                .withShowLlmResponses(extraction.showResponses())
                .ai();
        return LlmPropositionReviser
                .withLlm(extraction.extractionLlm())
                .withAi(ai);
    }

    @Bean
    MemoryProjector memoryProjector(Relations relations) {
        return DefaultMemoryProjector
                .withKnowledgeTypeClassifier(new RelationBasedKnowledgeTypeClassifier(relations));
    }
}
