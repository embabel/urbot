package com.embabel.urbot;

import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.urbot.user.DummyUrbotUserService;
import com.embabel.urbot.user.UrbotUser;
import com.embabel.urbot.user.UrbotUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link UrbotUserService} bean that the excluded
 * {@code SecurityConfiguration} normally supplies.
 * No web security is needed since the test runs with {@code web-application-type=none}.
 */
@Configuration
class TestSecurityConfiguration {

    @Bean
    UrbotUserService userService(NamedEntityDataRepository entityRepository) {
        return new DummyUrbotUserService(entityRepository,
                new UrbotUser("1", "Alice Agu", "alice"),
                new UrbotUser("2", "Ben Blossom", "ben")
        );
    }
}
