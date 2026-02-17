package com.embabel.urbot.security;

import com.embabel.urbot.user.DummyUrbotUserService;
import com.embabel.urbot.user.UrbotUserService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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

    public LoginView(UrbotUserService userService) {
        addClassName("login-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        loginForm.setAction("login");

        var logo = new Image("images/weltenchronik.jpg", "Urbot");
        logo.setWidth("80px");
        logo.addClassName("login-logo");

        var titleText = new VerticalLayout();
        titleText.setPadding(false);
        titleText.setSpacing(false);

        var title = new H1("Urbot");
        title.addClassName("login-title");

        var subtitle = new Span("Chatbot with RAG and memory");
        subtitle.addClassName("login-subtitle");

        titleText.add(title, subtitle);

        var header = new HorizontalLayout(logo, titleText);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(true);

        var legend = new Div();
        legend.addClassName("login-legend");
        if (userService instanceof DummyUrbotUserService dummy) {
            for (var user : dummy.getUsers()) {
                legend.add(createCredential(user.getUsername(), user.getUsername()));
            }
        }

        add(header, loginForm, legend);
    }

    private Span createCredential(String username, String password) {
        var span = new Span(username + " / " + password);
        span.addClassName("login-legend-credentials");
        return span;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Show error if login failed
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
