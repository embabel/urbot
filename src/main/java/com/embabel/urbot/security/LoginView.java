package com.embabel.urbot.security;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Login view for Urbot.
 */
@Route("login")
@PageTitle("Login | Urbot")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        addClassName("login-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        loginForm.setAction("login");

        var title = new H1("Urbot");
        title.addClassName("login-title");

        var subtitle = new Span("Chatbot template");
        subtitle.addClassName("login-subtitle");

        var legend = new Div();
        legend.addClassName("login-legend");
        legend.add(new Span("Demo accounts:"));
        legend.add(createUserRow("alice", "alice", "Admin"));
        legend.add(createUserRow("ben", "ben", "User"));

        add(title, subtitle, loginForm, legend);
    }

    private Div createUserRow(String username, String password, String role) {
        var row = new Div();
        row.addClassName("login-legend-row");
        var userSpan = new Span(username + " / " + password);
        userSpan.addClassName("login-legend-credentials");
        var roleSpan = new Span(role);
        roleSpan.addClassName("login-legend-role");
        row.add(userSpan, roleSpan);
        return row;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Show error if login failed
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
