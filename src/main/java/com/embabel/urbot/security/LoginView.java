package com.embabel.urbot.security;

import com.embabel.urbot.UrbotProperties;
import com.embabel.urbot.user.DummyUrbotUserService;
import com.embabel.urbot.user.UrbotUserService;
import com.embabel.vaadin.component.BaseLoginView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.ArrayList;
import java.util.List;

/**
 * Login view for Urbot.
 */
@Route("login")
@PageTitle("Login | Urbot")
@AnonymousAllowed
public class LoginView extends BaseLoginView {

    public LoginView(UrbotUserService userService, UrbotProperties properties) {
        super(
            "Urbot",
            properties.chat().tagline(),
            (properties.stylesheet() != null && !properties.stylesheet().isBlank())
                ? "images/" + properties.stylesheet() + ".jpg"
                : "images/weltenchronik.jpg",
            buildCredentials(userService)
        );
    }

    private static List<String> buildCredentials(UrbotUserService userService) {
        var credentials = new ArrayList<String>();
        if (userService instanceof DummyUrbotUserService dummy) {
            for (var user : dummy.getUsers()) {
                credentials.add(user.getUsername() + " / test");
            }
        }
        return credentials;
    }
}
