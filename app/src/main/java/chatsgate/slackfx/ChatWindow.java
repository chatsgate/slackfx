package chatsgate.slackfx;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.input.ClipboardContent;
import javafx.util.Duration;
import leasegate.frame.*;
import chatsgate.slackfx.core.Cx;

import module java.base;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import static luvjfx.Fx.*;

/// The chat window, in Slack's own frame (see Chrome): the sidebar of conversations, the open one with its write box, and
/// the function-key legend along the bottom (each entry is also a button). Closing hides it; slackfx stays in the tray and
/// keeps listening. A message written here goes straight to Slack: you are typing it yourself, so it needs no approval.
/// Only the CLI's writes go through the F2 approval window.
public final class ChatWindow {

    static final String BLUE = "#1d9bd1", TEAL = "#00897b", PURPLE = "#8e24aa",
        HINT = "Up/Down choose a conversation (or, in an empty write box, a message) · PgUp/PgDn scroll · Shift+Enter new line";

    final Cx cx;
    final Avatars avatars = new Avatars();
    final Unread unread;
    final ConvList convs;
    final MessageList messages;
    final WindowKeys keys = new WindowKeys(this);
    final TextField filter = new TextField();
    final Label status = new Label("");
    /// Arrowing through the list opens a conversation only once the selection has rested, not on every step.
    final PauseTransition settle = new PauseTransition(Duration.millis(250));
    MainFrame frame;
    volatile Instant lastActivity = Instant.EPOCH;
    boolean swept;

    public ChatWindow(Cx cx) {
        this.cx = cx;
        this.unread = new Unread(cx, this::redraw);
        this.convs = new ConvList(avatars, unread);
        this.messages = new MessageList(cx, avatars);
        messages.onShown = (conv, ts) -> { if (frame != null && frame.stage.isShowing()) unread.clear(conv, ts); };
    }

    void build() {
        var t0 = System.nanoTime();
        Fonts.load();
        frame = new MainFrame("slackfx - " + cx.session.teamName(), Look.ICONS.window(TaskbarTheme.isDark()), 0x2A0A2D, 0xF8F8F8, 0x431349);
        filter.setPromptText("Find a channel or person in " + cx.session.teamName());
        filter.getStyleClass().add("search-box");
        filter.textProperty().addListener((o, was, now) -> convs.filter(now));
        settle.setOnFinished(e -> openSelected());
        convs.view.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> settle.playFromStart());
        convs.view.setOnMouseClicked(e -> filter.requestFocus());
        var fkeys = new FKeys().dark()
            .bind(KeyCode.ENTER, "Open / Send", Parts.GREEN, keys::enter)
            .bind(KeyCode.F2, "Conversations", BLUE, filter::requestFocus)
            .bind(KeyCode.F3, "Write", BLUE, messages.compose::requestFocus)
            .bind(KeyCode.F5, "Older", TEAL, messages::older)
            .bind(KeyCode.F6, "Refresh", TEAL, this::refresh)
            .bind(KeyCode.F7, "Copy message", PURPLE, this::copy)
            .bind(KeyCode.F11, "Maximize", Parts.GREY, frame::toggleMaximized)
            .bind(KeyCode.ESCAPE, "Back / Hide", Parts.GREY, keys::escape);
        var team = new Label(cx.session.teamName());
        team.getStyleClass().add("team-name");
        var head = hbox().attr(h -> { h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(0, 16, 0, 16)); h.setMinHeight(49); }).nodes(fx(team));
        var sidebar = vbox(head, fx(convs.view)).styleClass("side").attr(v -> VBox.setVgrow(convs.view, Priority.ALWAYS)).node;
        var legend = vbox().spacing(2).styleClass("legend").attr(b -> b.setPadding(new Insets(4, 10, 6, 10)))
            .nodes(fx(fkeys.bar), fx(status), fx(Parts.small(HINT, "#c9b7cc"))).node;
        status.getStyleClass().add("status");
        status.visibleProperty().bind(status.textProperty().isNotEmpty());
        status.managedProperty().bind(status.visibleProperty());
        var root = (Region) Chrome.build(cx.session.teamName(), filter, sidebar, messages.node, legend);
        root.setPrefSize(1100, 720);
        var scene = frame.scene(root);
        scene.getStylesheets().add(ChatWindow.class.getResource("slack.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keys::keys);
        scene.addEventFilter(Event.ANY, e -> lastActivity = Instant.now());
        frame.trimWhenHidden();
        fkeys.install(scene);
        Log.info("window built in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        Bg.run(() -> { avatars.loadSaved(cx); Platform.runLater(this::redraw); });
        reload();
        Bg.run(() -> {
            try {
                avatars.load(cx);
                Platform.runLater(this::redraw);
            } catch (RuntimeException e) {
                Log.warn("could not list the people's pictures: " + e.getMessage());
            }
        });
    }

    void redraw() { convs.view.refresh(); messages.view.refresh(); }

    public void show() {
        var first = frame == null;
        if (first) build();
        frame.stage.show();
        if (first) Platform.runLater(() -> frame.fit(1100, 760));
        frame.stage.toFront();
        filter.requestFocus();
    }

    /// Opens the window on one conversation (what a clicked notification does).
    public void openConversation(String id) {
        show();
        filter.clear();
        convs.select(id);
        messages.compose.requestFocus();
    }

    /// True while you are looking at this conversation, so a new message in it needs no notification.
    public boolean watching(String conv) {
        return frame != null && frame.stage.isShowing() && frame.stage.isFocused() && conv.equals(messages.conv);
    }

    /// A live event, on the FX thread. The open conversation fetches its new tail (or reloads, for an edit, a deletion or a
    /// reaction); any other one lights up and, for a direct message, moves to the top. An unknown one reloads the list.
    public void arrived(JsonNode e, String conv) {
        if (frame == null) return;
        var type = e.path("type").asText();
        var sub = e.path("subtype").asText("");
        if (conv.equals(messages.conv)) {
            if (type.startsWith("reaction_") || sub.equals("message_changed") || sub.equals("message_deleted")) messages.open(conv, messages.title.getText());
            else messages.newer();
            return;
        }
        if (!Feed.isNewMessage(e) || cx.session.userId().equals(e.path("user").asText())) return;
        if (!convs.has(conv)) { reload(); return; }
        unread.bump(conv);
        convs.touch(conv);
    }

    /// Shows the saved list at once (first open only), then Slack's current one; the first time, sweeps for unread.
    void reload() {
        Bg.run(() -> {
            var saved = ConvList.cached(cx);
            if (!saved.isEmpty()) Platform.runLater(() -> { if (convs.rows.isEmpty()) convs.show(saved); });
            try {
                var t0 = System.nanoTime();
                var all = ConvList.load(cx);
                Log.info("listed " + all.size() + " conversations in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
                Platform.runLater(() -> {
                    convs.show(all);
                    if (!swept) { swept = true; unread.sweep(cx, convs.ids()); }
                });
            } catch (RuntimeException e) {
                Platform.runLater(() -> status.setText("could not list the conversations: " + e.getMessage()));
            }
        });
    }

    void openSelected() {
        var c = convs.selected();
        if (c != null && !c.path("id").asText().equals(messages.conv)) messages.open(c.path("id").asText(), ConvList.title(c));
    }

    void refresh() {
        reload();
        var c = convs.selected();
        if (c != null) messages.open(c.path("id").asText(), ConvList.title(c));
    }

    void send() {
        var text = messages.compose.getText().strip();
        var conv = messages.conv;
        if (text.isEmpty() || conv == null) return;
        messages.compose.clear();
        status.setText("sending...");
        Bg.run(() -> {
            try {
                cx.api.send("chat.postMessage", java.util.Map.of("channel", conv, "text", text));
                Log.info("sent " + text.length() + " chars from the window");
                Platform.runLater(() -> { status.setText("sent"); messages.newer(); });
            } catch (RuntimeException e) {
                Platform.runLater(() -> { status.setText("NOT sent (" + e.getMessage() + "): the text is back in the box"); messages.compose.setText(text); });
            }
        });
    }

    void copy() {
        var t = messages.selectedText();
        if (t == null) { status.setText("choose a message first: click it, or press Up in the empty write box, then F7"); return; }
        var c = new ClipboardContent();
        c.putString(t);
        Clipboard.getSystemClipboard().setContent(c);
        status.setText("copied");
    }
}
