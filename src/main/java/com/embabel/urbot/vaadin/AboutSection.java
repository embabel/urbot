package com.embabel.urbot.vaadin;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * About section showing application information, credits, and links.
 */
public class AboutSection extends VerticalLayout {

    public AboutSection() {
        setPadding(true);
        setSpacing(true);
        getStyle().set("font-size", "1.25rem");

        // Primary author
        var authorSection = new VerticalLayout();
        authorSection.setPadding(false);
        authorSection.setSpacing(false);
        authorSection.getStyle().set("gap", "var(--lumo-space-xs)");

        authorSection.add(sectionLabel("Primary author"));

        var authorName = new Span("Rod Johnson");
        authorName.getStyle()
                .set("font-size", "1.5rem")
                .set("font-weight", "600")
                .set("color", "var(--lumo-primary-color)")
                .set("font-family", "var(--lumo-font-family)");
        authorSection.add(authorName);

        var socialLinks = new HorizontalLayout();
        socialLinks.setAlignItems(FlexComponent.Alignment.CENTER);
        socialLinks.setSpacing(true);
        socialLinks.getStyle().set("gap", "var(--lumo-space-m)");
        socialLinks.add(
                socialLink("https://twitter.com/springrod",
                        "https://cdn.jsdelivr.net/npm/simple-icons@v9/icons/x.svg", "X (Twitter)", "@springrod"),
                socialLink("https://www.linkedin.com/in/johnsonroda/",
                        "https://cdn.jsdelivr.net/npm/simple-icons@v9/icons/linkedin.svg", "LinkedIn", "LinkedIn")
        );
        authorSection.add(socialLinks);
        add(authorSection);

        // Powered by
        var poweredBy = sectionLabel("Powered by");
        poweredBy.getStyle().set("margin-top", "var(--lumo-space-l)");
        add(poweredBy);

        var logos = new HorizontalLayout();
        logos.setAlignItems(FlexComponent.Alignment.CENTER);
        logos.setSpacing(true);
        logos.getStyle().set("gap", "var(--lumo-space-l)").set("flex-wrap", "wrap");
        logos.add(
                logoLink("https://spring.io", "https://spring.io/img/spring.svg", "Spring Boot"),
                logoLink("https://neo4j.com", "https://dist.neo4j.com/wp-content/uploads/20210423062553/neo4j-social-share-21.png", "Neo4j"),
                logoLink("https://vaadin.com", "https://brand.vaadin.com/images/vaadin-logo-dark.svg", "Vaadin"),
                logoLink("https://www.apache.org", "https://www.apache.org/foundation/press/kit/asf_logo_url.png", "Apache Software Foundation"),
                embabelLogo()
        );
        add(logos);

        // Built with Claude Code
        var claudeInfo = new HorizontalLayout();
        claudeInfo.setAlignItems(FlexComponent.Alignment.CENTER);
        claudeInfo.setSpacing(true);
        claudeInfo.getStyle().set("margin-top", "var(--lumo-space-l)");
        var claudeLogo = new Image("https://cdn.worldvectorlogo.com/logos/anthropic-1.svg", "Claude");
        claudeLogo.setHeight("24px");
        claudeLogo.getStyle().set("vertical-align", "middle");
        claudeInfo.add(new Span("Built using"), claudeLogo, externalLink("https://claude.ai/claude-code", "Claude Code"));
        add(claudeInfo);

        // License
        var license = new Span("Licensed under the Apache License 2.0");
        license.getStyle()
                .set("font-size", "1rem")
                .set("color", "var(--lumo-secondary-text-color)");
        add(license);

        // Copyright
        var copyright = new Span("\u00A9 Embabel 2026");
        copyright.getStyle()
                .set("font-size", "1rem")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "var(--lumo-space-l)");
        add(copyright);
    }

    private static Span sectionLabel(String text) {
        var label = new Span(text);
        label.getStyle()
                .set("font-size", "0.9rem")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.1em");
        return label;
    }

    private static Anchor socialLink(String url, String iconUrl, String alt, String title) {
        var icon = new Image(iconUrl, alt);
        icon.setHeight("20px");
        icon.getStyle().set("filter", "invert(40%)");
        var link = new Anchor(url, icon);
        link.setTarget("_blank");
        link.getElement().setAttribute("title", title);
        return link;
    }

    private static Anchor logoLink(String url, String logoUrl, String alt) {
        var logo = new Image(logoUrl, alt);
        logo.setHeight("48px");
        var link = new Anchor(url, logo);
        link.setTarget("_blank");
        return link;
    }

    private static Anchor embabelLogo() {
        var logo = new Image(
                "https://raw.githubusercontent.com/embabel/embabel-agent/refs/heads/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg",
                "Embabel");
        logo.setHeight("80px");
        logo.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        var link = new Anchor("https://embabel.com", logo);
        link.setTarget("_blank");
        return link;
    }

    private static Anchor externalLink(String url, String text) {
        var link = new Anchor(url, text);
        link.setTarget("_blank");
        return link;
    }
}
