package com.embabel.urbot.user;

import com.embabel.agent.api.identity.UserService;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Service for managing Urbot users.
 * Extends UserDetailsService so a single bean drives both
 * application users and Spring Security authentication.
 */
public interface UrbotUserService extends UserService<UrbotUser>, UserDetailsService {

    /**
     * Get the currently authenticated user.
     */
    UrbotUser getAuthenticatedUser();

}
