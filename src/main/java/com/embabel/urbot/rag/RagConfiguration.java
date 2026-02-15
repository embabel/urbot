package com.embabel.urbot.rag;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.filter.PropertyFilter;
import com.embabel.agent.rag.ingestion.ChunkTransformer;
import com.embabel.agent.rag.ingestion.transform.AddTitlesChunkTransformer;
import com.embabel.agent.rag.neo.drivine.DrivineCypherSearch;
import com.embabel.agent.rag.neo.drivine.DrivineStore;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.urbot.UrbotProperties;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.GraphObjectManagerFactory;
import org.drivine.manager.PersistenceManager;
import org.drivine.manager.PersistenceManagerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(UrbotProperties.class)
class RagConfiguration {

    @Bean
    PersistenceManager persistenceManager(PersistenceManagerFactory factory) {
        return factory.get("neo");
    }

    @Bean
    GraphObjectManager graphObjectManager(GraphObjectManagerFactory factory) {
        return factory.get("neo");
    }

    @Bean
    @Primary
    EmbeddingService embeddingService(ModelProvider modelProvider) {
        return modelProvider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);
    }

    @Bean
    ChunkTransformer chunkTransformer() {
        return AddTitlesChunkTransformer.INSTANCE;
    }

    @Bean
    @Primary
    DrivineStore drivineStore(
            PersistenceManager persistenceManager,
            PlatformTransactionManager platformTransactionManager,
            EmbeddingService embeddingService,
            ChunkTransformer chunkTransformer,
            UrbotProperties properties) {
        var store = new DrivineStore(
                persistenceManager,
                properties.neoRag(),
                properties.ingestion(),
                chunkTransformer,
                embeddingService,
                platformTransactionManager,
                new DrivineCypherSearch(persistenceManager)
        );
        store.provision();
        return store;
    }

    /**
     * Documents shared between all users
     */
    @Bean
    LlmReference globalDocuments(SearchOperations searchOperations) {
        return new ToolishRag(
                "shared_docs",
                "Shared documents",
                searchOperations)
                .withMetadataFilter(
                        new PropertyFilter.Eq(
                                DocumentService.Context.CONTEXT_KEY,
                                DocumentService.Context.GLOBAL_CONTEXT
                        )).withUnfolding();
    }

}
