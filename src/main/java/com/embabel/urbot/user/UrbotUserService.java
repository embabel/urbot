package com.embabel.urbot.user;

import com.embabel.agent.api.identity.UserService;

/**
 * Service for managing Urbot users.
 */
public interface UrbotUserService extends UserService<UrbotUser> {

    /**
     * Get the currently authenticated user.
     */
    UrbotUser getAuthenticatedUser();
}
