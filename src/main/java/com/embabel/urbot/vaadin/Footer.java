package com.embabel.urbot.vaadin;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Footer component showing copyright and document statistics.
 */
public class Footer extends HorizontalLayout {

    public Footer() {
        setWidthFull();
        setPadding(false);
        setSpacing(true);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassName("app-footer");

        var copyright = new Span("© Embabel 2026");
        copyright.addClassName("footer-copyright");

        add(copyright);
    }
}
