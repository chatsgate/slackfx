package chatsgate.slackfx;

import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.scene.shape.SVGPath;

import module java.base;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import static luvjfx.Fx.*;

/// One sidebar row, sized as Slack's (28 px high, content 32 px in, matching the section label after its caret). The mark
/// before the name is # for a public channel, a padlock for a private one, the person's picture for a direct message, and
/// the number of people for a group. Unread rows turn bright and heavy, and a direct message shows its count in a red
/// badge, as Slack does. A section heading row cannot be selected, so the mouse cannot land on it either.
public final class ConvCell extends ListCell<ObjectNode> {

    /// Slack's padlock in a 12 x 13 box: the shackle as a band, then the body.
    static final String LOCK = "M3 6V4.5A3 3 0 0 1 9 4.5V6H7.6V4.5A1.6 1.6 0 0 0 4.4 4.5V6Z"
        + "M1.5 6H10.5A1 1 0 0 1 11.5 7V12A1 1 0 0 1 10.5 13H1.5A1 1 0 0 1 0.5 12V7A1 1 0 0 1 1.5 6Z";

    final Avatars avatars;
    final Unread unread;
    final Label hash = new Label("#"), name = new Label(), heading = new Label(), badge = new Label();
    final SVGPath lock = new SVGPath();
    final AvatarView face = new AvatarView(16);
    final StackPane mark = new StackPane(hash, lock, face);
    final Region gap = new Region();
    final HBox line = hbox().spacing(8).attr(h -> { h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(0, 16, 0, 32)); h.setMinHeight(28); })
        .nodes(fx(mark), fx(name), fx(gap), fx(badge)).node;
    final HBox head = hbox().attr(h -> { h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(10, 16, 0, 16)); h.setMinHeight(34); })
        .nodes(fx(heading)).node;

    public ConvCell(Avatars avatars, Unread unread) {
        this.avatars = avatars;
        this.unread = unread;
        setPrefWidth(0);
        lock.setContent(LOCK);
        lock.getStyleClass().add("conv-lock");
        hash.getStyleClass().add("conv-hash");
        name.getStyleClass().add("conv-name");
        name.setMinWidth(0);
        heading.getStyleClass().add("conv-heading");
        badge.getStyleClass().add("badge");
        HBox.setHgrow(gap, Priority.ALWAYS);
        mark.setMinSize(16, 16);
        mark.setPrefSize(16, 16);
        mark.setMaxSize(16, 16);
    }

    @Override protected void updateItem(ObjectNode c, boolean empty) {
        super.updateItem(c, empty);
        if (empty || c == null) { setGraphic(null); setDisable(false); return; }
        if (ConvList.heading(c)) {
            setDisable(true);
            heading.setText("▾   " + c.path("section").asText());
            setGraphic(head);
            return;
        }
        setDisable(false);
        var id = c.path("id").asText();
        var kind = c.path("kind").asText();
        var dm = kind.equals("im") || kind.equals("mpim");
        hash.setVisible(kind.equals("public"));
        lock.setVisible(kind.equals("private"));
        face.setVisible(dm);
        if (kind.equals("im")) face.set(avatars.of(c.path("user").asText()), c.path("label").asText(), c.path("user").asText());
        else if (kind.equals("mpim")) face.set(null, String.valueOf(c.path("label").asText().split(", ").length), id);
        name.setText(c.path("label").asText());
        var lit = unread.has(id);
        for (var n : List.of(name, hash)) n.getStyleClass().remove("unread");
        lock.getStyleClass().remove("unread");
        if (lit) { name.getStyleClass().add("unread"); hash.getStyleClass().add("unread"); lock.getStyleClass().add("unread"); }
        badge.setText(String.valueOf(unread.count(id)));
        MsgCell.show(badge, lit && dm);
        setGraphic(line);
    }
}
