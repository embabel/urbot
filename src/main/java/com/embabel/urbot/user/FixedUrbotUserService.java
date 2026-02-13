package com.embabel.urbot.user;

import org.jetbrains.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Hardcoded user service implementation for development/demo purposes.
 */
@Service
public class FixedUrbotUserService implements UrbotUserService {

    private static final Map<String, UrbotUser> USERS_BY_USERNAME = Map.of(
            "admin", new UrbotUser("1", "Administrator", "admin"),
            "user", new UrbotUser("2", "Demo User", "user")
    );

    private static final Map<String, UrbotUser> USERS_BY_ID = Map.of(
            "1", USERS_BY_USERNAME.get("admin"),
            "2", USERS_BY_USERNAME.get("user")
    );

    @Override
    public UrbotUser getAuthenticatedUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails userDetails) {
            var user = USERS_BY_USERNAME.get(userDetails.getUsername());
            if (user != null) {
                return user;
            }
        }
        // Return anonymous user if not authenticated
        return new UrbotUser(UUID.randomUUID().toString(), "Anonymous", "anonymous");
    }

    @Override
    @Nullable
    public UrbotUser findById(String id) {
        return USERS_BY_ID.get(id);
    }

    @Override
    @Nullable
    public UrbotUser findByUsername(String username) {
        return USERS_BY_USERNAME.get(username);
    }

    @Override
    @Nullable
    public UrbotUser findByEmail(String email) {
        return null;
    }
}
