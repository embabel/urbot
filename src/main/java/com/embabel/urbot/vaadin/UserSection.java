package com.embabel.urbot.vaadin;

import com.embabel.urbot.user.UrbotUser;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * User section component showing clickable avatar and name.
 * Clicking opens the UserDrawer for personal document management.
 */
class UserSection extends HorizontalLayout {

    private Runnable onClickHandler;

    UserSection(UrbotUser user) {
        setAlignItems(FlexComponent.Alignment.CENTER);
        setSpacing(true);

        // Profile chip with avatar and name
        var profileChip = new HorizontalLayout();
        profileChip.addClassName("profile-chip");
        profileChip.addClassName("profile-chip-clickable");
        profileChip.setAlignItems(FlexComponent.Alignment.CENTER);
        profileChip.setSpacing(false);

        // Avatar with initials
        var initials = getInitials(user.getDisplayName());
        var avatar = new Div();
        avatar.setText(initials);
        avatar.addClassName("user-avatar");

        var userName = new Span(user.getDisplayName());
        userName.addClassName("user-name");

        profileChip.add(avatar, userName);
        profileChip.getElement().addEventListener("click", e -> {
            if (onClickHandler != null) {
                onClickHandler.run();
            }
        });

        add(profileChip);
    }

    void setOnClickHandler(Runnable handler) {
        this.onClickHandler = handler;
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        var parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
