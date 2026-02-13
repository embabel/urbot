package com.embabel.urbot.user;

import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Hardcoded user service implementation for development/demo purposes.
 */
public record DummyUrbotUserService(
        List<UrbotUser> users
) implements UrbotUserService {


    @Override
    public UrbotUser getAuthenticatedUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails userDetails) {
            var user = users.stream().filter(u -> Objects.equals(u.getUsername(), userDetails.getUsername())).findFirst();
            if (user.isPresent()) {
                return user.get();
            }
        }
        // Return anonymous user if not authenticated
        return new UrbotUser(UUID.randomUUID().toString(), "Anonymous", "anonymous");
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
