package com.embabel.bot.astrid;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.tool.Subagent;
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
public class AstridConfiguration {

    @Bean
    @Primary
    UrbotUserService astridUserService(NamedEntityDataRepository entityRepository) {
        return new DummyUrbotUserService(entityRepository,
                new UrbotUser("1", "Alice Agu", "alice"),
                new UrbotUser("3", "Cassie Silver", "cassie")
        );
    }

    @Bean
    Relations astridRelations() {
        return Relations.empty()
                .withPredicatesForSubject(
                        UrbotUser.class, KnowledgeType.SEMANTIC,
                        "lives in", "visited", "from", "studied at",
                        "enjoys", "loves", "reads", "listens to", "plays"
                )
                .withSemanticBetween("UrbotUser", "Pet", "owns", "user owns a pet")
                .withSemanticBetween("UrbotUser", "Company", "works_at", "user works at a company")
                .withSemanticBetween("UrbotUser", "Place", "lives_in", "user lives in a place")
                .withSemanticBetween("UrbotUser", "Place", "comes_from", "user comes from a place")
                .withSemanticBetween("UrbotUser", "Place", "has_visited", "user has visited a place")
                .withSemanticBetween("UrbotUser", "Place", "wants_to_visit", "user wants to visit a place")
                .withSemanticBetween("UrbotUser", "Place", "from", "user is from a place")
                .withSemanticBetween("UrbotUser", "Organization", "works_at", "user works at an organization")
                .withSemanticBetween("UrbotUser", "Organization", "studied_at", "user studied at an organization")
                .withSemanticBetween("UrbotUser", "Person", "knows", "user knows a person")
                .withSemanticBetween("UrbotUser", "Food", "likes", "user likes a food")
                .withSemanticBetween("UrbotUser", "Hobby", "enjoys", "user enjoys a hobby")
                .withSemanticBetween("UrbotUser", "Band", "listens_to", "user listens to a band")
                .withSemanticBetween("UrbotUser", "Book", "reads", "user reads a book")
                .withSemanticBetween("UrbotUser", "Goal", "is_working_toward", "user is working toward a goal");
    }

    @Bean
    LlmReference astrologyDocuments(SearchOperations searchOperations) {
        return new ToolishRag(
                "astrology_docs",
                "Shared astrology documents for answering questions about astrology, horoscopes, and related topics. Use this to answer user questions about astrology, but not for general knowledge or personal information about the user.",
                searchOperations)
                .withMetadataFilter(
                        new PropertyFilter.Eq(
                                DocumentService.Context.CONTEXT_KEY,
                                DocumentService.Context.GLOBAL_CONTEXT
                        )).withUnfolding();
    }

    // As a Tool, this will be automatically picked up
    @Bean
    Subagent dailyHoroscope() {
        return Subagent.ofClass(DailyHoroscopeAgent.class)
                .consuming(DailyHoroscopeAgent.HoroscopeRequest.class);
    }
}
