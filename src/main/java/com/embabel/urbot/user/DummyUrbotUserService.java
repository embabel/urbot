package com.embabel.urbot.user;

import com.embabel.agent.rag.model.SimpleNamedEntityData;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hardcoded user service implementation for development/demo purposes.
 * DO NOT USE IN PRODUCTION - implement UrbotUserService with proper user and password management.
 */
public class DummyUrbotUserService implements UrbotUserService {

    private static final Logger logger = LoggerFactory.getLogger(DummyUrbotUserService.class);

    private final List<UrbotUser> users;
    private final NamedEntityDataRepository entityRepository;
    private final Set<String> persistedUserIds = ConcurrentHashMap.newKeySet();

    public DummyUrbotUserService(List<UrbotUser> users, NamedEntityDataRepository entityRepository) {
        this.users = users;
        this.entityRepository = entityRepository;
    }

    @Override
    public UrbotUser getAuthenticatedUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails userDetails) {
            var user = users.stream().filter(u -> Objects.equals(u.getUsername(), userDetails.getUsername())).findFirst();
            if (user.isPresent()) {
                ensureEntityExists(user.get());
                return user.get();
            }
        }
        // Return anonymous user if not authenticated
        return new UrbotUser(UUID.randomUUID().toString(), "Anonymous", "anonymous");
    }

    private void ensureEntityExists(UrbotUser user) {
        if (persistedUserIds.add(user.getId())) {
            entityRepository.save(new SimpleNamedEntityData(
                    user.getId(), null, user.getName(), user.getDescription(),
                    user.labels(), Map.of(), Map.of(), null));
            logger.info("Persisted user entity to graph: {} ({})", user.getName(), user.getId());
        }
    }

    @Override
    @Nullable
    public UrbotUser findById(String id) {
        return users.stream().filter(u -> Objects.equals(u.getId(), id)).findFirst().orElse(null);
    }

    @Override
    @Nullable
    public UrbotUser findByUsername(@NonNull String username) {
        return users.stream().filter(u -> Objects.equals(u.getUsername(), username)).findFirst().orElse(null);
    }

    @Override
    @Nullable
    public UrbotUser findByEmail(@NonNull String email) {
        return null;
    }
}
