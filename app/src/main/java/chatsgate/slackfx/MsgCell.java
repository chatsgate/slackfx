package chatsgate.slackfx;

import com.fasterxml.jackson.databind.node.ObjectNode;

import module java.base;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextFlow;

import static luvjfx.Fx.*;

/// One message, measured from Slack's own (app.slack.com, 2026-10-27): 36 px rounded picture, 8 px gap, the name in 15 px
/// weight 900, the time in 12 px grey, the text in 15 px on 22 px lines. A run (same sender, same day, within five minutes)
/// drops the picture and the name. A new day puts its date in a rounded pill on a line. Join/leave notices are dimmed.
public final class MsgCell extends ListCell<ObjectNode> {

    static final double RUN_SECONDS = 5 * 60;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM"), YEAR = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"),
        HOUR = DateTimeFormatter.ofPattern("HH:mm");

    final MessageList list;
    final Label who = new Label(), when = new Label(), pill = new Label(), meta = new Label();
    final TextFlow text = new TextFlow();
    final AvatarView face = new AvatarView(36);
    final VBox attach = new VBox(6);
    final FlowPane reacts = new FlowPane(4, 4);
    final Region line = new Region();
    final StackPane day = new StackPane(line, pill);
    final HBox head = hbox().spacing(8).attr(h -> h.setAlignment(Pos.BASELINE_LEFT)).nodes(fx(who), fx(when)).node;
    final VBox body = vbox().spacing(2).attr(b -> HBox.setHgrow(b, Priority.ALWAYS)).nodes(fx(head), fx(text), fx(attach), fx(meta), fx(reacts)).node;
    final StackPane slot = new StackPane(face);
    final HBox row = hbox().spacing(8).nodes(fx(slot), fx(body)).node;
    final VBox box = vbox(fx(day), fx(row)).node;

    public MsgCell(MessageList list) {
        this.list = list;
        setPrefWidth(0);
        who.getStyleClass().add("msg-who");
        when.getStyleClass().add("msg-when");
        meta.getStyleClass().add("msg-meta");
        meta.setWrapText(true);
        pill.getStyleClass().add("day-pill");
        line.getStyleClass().add("day-line");
        line.setMaxHeight(1);
        day.setPadding(new Insets(16, 0, 8, 0));
        slot.setMinWidth(36);
        slot.setAlignment(Pos.TOP_CENTER);
        text.setLineSpacing(4);
        text.maxWidthProperty().bind(list.view.widthProperty().subtract(80));
        attach.maxWidthProperty().bind(list.view.widthProperty().subtract(80));
        meta.maxWidthProperty().bind(list.view.widthProperty().subtract(80));
    }

    @Override protected void updateItem(ObjectNode m, boolean empty) {
        super.updateItem(m, empty);
        if (empty || m == null) { setGraphic(null); return; }
        var items = getListView().getItems();
        var i = getIndex();
        var prev = i > 0 && i <= items.size() ? items.get(i - 1) : null;
        var t = time(m);
        var newDay = prev == null || !time(prev).toLocalDate().equals(t.toLocalDate());
        pill.setText(t.getYear() == LocalDate.now().getYear() ? t.format(DAY) : t.format(YEAR));
        show(day, newDay);
        var from = list.author(m);
        var run = prev != null && !newDay && !m.has("subtype") && !prev.has("subtype") && from.equals(list.author(prev))
            && seconds(m) - seconds(prev) < RUN_SECONDS;
        row.setPadding(run ? new Insets(2, 20, 2, 20) : new Insets(8, 20, 4, 20));
        face.setVisible(!run);
        if (!run) face.set(list.avatars.face(m), from, m.path("user").asText(from));
        who.setText(from);
        when.setText(t.format(HOUR) + (m.has("edited") ? "  (edited)" : ""));
        show(head, !run);
        Mrkdwn.fill(text, MsgParts.emojify(m.path("text").asText("")), list.labels);
        text.setOpacity(m.has("subtype") ? 0.65 : 1);
        attach.getChildren().clear();
        for (var a : MsgParts.attachments(m)) attach.getChildren().add(attachment(a));
        show(attach, !attach.getChildren().isEmpty());
        var x = MsgParts.meta(m);
        meta.setText(x);
        show(meta, !x.isEmpty());
        reacts.getChildren().clear();
        for (var r : MsgParts.reactions(m)) { var p = new Label(r); p.getStyleClass().add("reaction"); reacts.getChildren().add(p); }
        show(reacts, !reacts.getChildren().isEmpty());
        setGraphic(box);
    }

    /// An attachment as Slack draws it: a 4 px rounded grey bar, then its text.
    Node attachment(String mrkdwn) {
        var bar = new Region();
        bar.getStyleClass().add("attach-bar");
        bar.setMinWidth(4);
        bar.setMaxWidth(4);
        var flow = new TextFlow();
        flow.setLineSpacing(3);
        Mrkdwn.fill(flow, MsgParts.emojify(mrkdwn), list.labels);
        return hbox().spacing(12).nodes(fx(bar), fx(flow)).node;
    }

    static double seconds(ObjectNode m) { return Double.parseDouble(m.path("ts").asText("0")); }

    static ZonedDateTime time(ObjectNode m) { return Instant.ofEpochMilli((long) (seconds(m) * 1000)).atZone(ZoneId.systemDefault()); }

    static void show(Node n, boolean on) { n.setVisible(on); n.setManaged(on); }
}
