package com.embabel.urbot.user;

import com.embabel.agent.api.identity.User;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.filter.PropertyFilter;
import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.urbot.rag.DocumentService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * User model for Urbot.
 * Implements NamedEntity so users can be referenced in DICE propositions.
 */
public class UrbotUser implements User, NamedEntity {

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

    /**
     * Alias for effectiveContext() matching the Memory API convention.
     */
    public String currentContext() {
        return effectiveContext();
    }

    public void setCurrentContextName(String currentContextName) {
        this.currentContextName = currentContextName;
    }

    @Override
    public @NotNull String getId() {
        return id;
    }

    @Override
    public @NotNull String getDisplayName() {
        return displayName;
    }

    @Override
    public @NotNull String getUsername() {
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

    // NamedEntity implementation

    @NotNull
    @Override
    public String getName() {
        return displayName;
    }

    @NotNull
    @Override
    public String getDescription() {
        return "User: " + displayName;
    }

    @Nullable
    @Override
    public String getUri() {
        return null;
    }

    @NotNull
    @Override
    public Map<String, Object> getMetadata() {
        return Map.of();
    }

    @NotNull
    @Override
    public Set<String> labels() {
        return Set.of("UrbotUser");
    }

    @NotNull
    @Override
    public String embeddableValue() {
        return getName() + ": " + getDescription();
    }

    @NotNull
    public LlmReference personalDocs(SearchOperations searchOperations) {
        return new ToolishRag(
                "user_docs",
                "User's own documents",
                searchOperations)
                .withMetadataFilter(
                        new PropertyFilter.Eq(
                                DocumentService.Context.CONTEXT_KEY,
                                effectiveContext()
                        )
                ).withUnfolding();
    }
}
