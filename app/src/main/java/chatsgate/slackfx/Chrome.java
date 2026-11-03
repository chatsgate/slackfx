package chatsgate.slackfx;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import module java.base;
import javafx.geometry.*;
import javafx.scene.layout.*;

import static luvjfx.Fx.*;

/// The frame around the chat, laid out as Slack's own window (measured from app.slack.com on 2026-10-27): the theme layer
/// behind everything; a 40 px top bar with the search box centred; a 70 px rail on the left holding the workspace tile; and
/// the workspace panel, inset 4 px right and bottom with its top-left corner rounded, holding the sidebar and the message
/// pane. The function-key legend, which Slack does not have, sits along the bottom on the theme layer.
public final class Chrome {

    private Chrome() { }

    public static Parent build(String team, TextField search, Node sidebar, Node main, Node legend) {
        search.setPrefWidth(525);
        search.setMaxWidth(525);
        var top = hbox().styleClass("top-bar").attr(h -> { h.setAlignment(Pos.CENTER); h.setMinHeight(40); h.setPrefHeight(40); }).nodes(fx(search));
        var tile = new Label(team.isBlank() ? "?" : team.substring(0, 1).toUpperCase());
        tile.getStyleClass().add("team-tile");
        tile.setMinSize(36, 36);
        tile.setPrefSize(36, 36);
        tile.setMaxSize(36, 36);
        tile.setAlignment(Pos.CENTER);
        var rail = vbox().attr(v -> { v.setAlignment(Pos.TOP_CENTER); v.setMinWidth(70); v.setPrefWidth(70); v.setPadding(new Insets(8, 0, 0, 0)); })
            .nodes(fx(tile));
        var panel = hbox(fx(sidebar), fx(main)).styleClass("workspace").attr(h -> HBox.setHgrow(main, Priority.ALWAYS));
        var holder = vbox(panel).attr(v -> { v.setPadding(new Insets(0, 4, 4, 0)); VBox.setVgrow(panel.node, Priority.ALWAYS); });
        var middle = hbox(rail, holder).attr(h -> HBox.setHgrow(holder.node, Priority.ALWAYS));
        return vbox(top, middle, fx(legend)).styleClass("theme").attr(v -> VBox.setVgrow(middle.node, Priority.ALWAYS)).node;
    }
}
