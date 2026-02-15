package com.embabel.urbot.security;

import com.embabel.urbot.user.DummyUrbotUserService;
import com.embabel.urbot.user.UrbotUser;
import com.embabel.urbot.user.UrbotUserService;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.util.List;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security configuration for Urbot.
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/images/**")).permitAll()
        );
        super.configure(http);
        setLoginView(http, LoginView.class);

        // Configure logout
        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .permitAll()
        );
    }

    @Bean
    public UserDetailsService userDetailsService() {
        var alice = User.withDefaultPasswordEncoder()
                .username("alice")
                .password("alice")
                .roles("ADMIN", "USER")
                .build();

        var ben = User.withDefaultPasswordEncoder()
                .username("ben")
                .password("ben")
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(alice, ben);
    }

    @Bean
    UrbotUserService userService() {
        return new DummyUrbotUserService(java.util.List.of(
                new UrbotUser("1", "Alice Agu", "alice"),
                new UrbotUser("2", "Ben Blossom", "ben")
        ));
    }
}
