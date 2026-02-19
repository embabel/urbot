package com.embabel.flicker;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.filter.PropertyFilter;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.dice.common.KnowledgeType;
import com.embabel.dice.common.Relations;
import com.embabel.urbot.rag.DocumentService;
import com.embabel.urbot.user.DummyUrbotUserService;
import com.embabel.urbot.user.UrbotUser;
import com.embabel.urbot.user.UrbotUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class FlickerConfiguration {

    @Bean
    @Primary
    UrbotUserService flickerUserService(NamedEntityDataRepository entityRepository) {
        return new DummyUrbotUserService(entityRepository,
                new UrbotUser("rod_johnson", "Rod Johnson", "rod"),
                new UrbotUser("sam_spade", "Sam Spade", "sam")
        );
    }

    @Bean
    Relations flickerRelations() {
        return Relations.empty()
                .withPredicatesForSubject(
                        UrbotUser.class, KnowledgeType.SEMANTIC,
                        "watched", "likes", "admires", "prefers", "rated"
                )
                .withSemanticBetween("UrbotUser", "Movie", "watched", "user watched a movie")
                .withSemanticBetween("UrbotUser", "Movie", "wants_to_watch", "user wants to watch a movie")
                .withSemanticBetween("UrbotUser", "Movie", "rated", "user rated a movie")
                .withSemanticBetween("UrbotUser", "Director", "admires", "user admires a director")
                .withSemanticBetween("UrbotUser", "Actor", "likes", "user likes an actor")
                .withSemanticBetween("UrbotUser", "Genre", "prefers", "user prefers a genre")
                .withSemanticBetween("UrbotUser", "Place", "lives_in", "user lives in a place")
                .withSemanticBetween("UrbotUser", "Place", "from", "user is from a place")
                .withSemanticBetween("UrbotUser", "Person", "knows", "user knows a person")
                .withSemanticBetween("UrbotUser", "StreamingPlatform", "subscribes_to", "user subscribes to a streaming platform");
    }

    @Bean
    LlmReference movieDocuments(SearchOperations searchOperations) {
        return new ToolishRag(
                "movie_docs",
                "Shared movie documents for answering questions about films, directors, actors, and related topics. Use this to answer user questions about movies, but not for general knowledge or personal information about the user.",
                searchOperations)
                .withMetadataFilter(
                        new PropertyFilter.Eq(
                                DocumentService.Context.CONTEXT_KEY,
                                DocumentService.Context.GLOBAL_CONTEXT
                        )).withUnfolding();
    }
}
