package com.embabel.urbot.user;

import com.embabel.agent.api.identity.User;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * User model for Urbot.
 */
public class UrbotUser implements User {

    private final String id;
    private final String displayName;
    private final String username;

    private String currentContextName;

    public UrbotUser(String id, String displayName, String username) {
        this.id = id;
        this.displayName = displayName;
        this.username = username;
        this.currentContextName = "personal";
    }

    /**
     * The effective context is a unique identifier for the user's current context, combining their user ID and the context name.
     */
    public String effectiveContext() {
        return id + "_" + currentContextName;
    }

    public void setCurrentContextName(String currentContextName) {
        this.currentContextName = currentContextName;
    }

    @Override
    public @NonNull String getId() {
        return id;
    }

    @Override
    public @NonNull String getDisplayName() {
        return displayName;
    }

    @Override
    public @NonNull String getUsername() {
        return username;
    }

    @Override
    @Nullable
    public String getEmail() {
        return null;
    }

    public String getCurrentContextName() {
        return currentContextName;
    }
}
