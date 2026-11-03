package chatsgate.slackfx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import chatsgate.slackfx.core.Cx;

import module java.base;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import static luvjfx.Fx.*;

/// The message pane, laid out as Slack's: a 49 px header with the conversation's name, the messages (latest page; older
/// ones on F5 or scrolling to the top), and the write box 20 px from the sides. Slack is called only on the background
/// lane; the FX thread sees finished pages and a ready map of names.
public final class MessageList {

    static final int PAGE = 50;
    static final Pattern MENTION = Pattern.compile("<@([UW][A-Z0-9]+)|<#([CG][A-Z0-9]+)>");

    final Cx cx;
    final Avatars avatars;
    final Label title = new Label(" "), hint = new Label();
    final ListView<ObjectNode> view = new ListView<>();
    final TextArea compose = new TextArea();
    final Map<String, String> labels = new HashMap<>();
    final VBox node;
    /// Told (conversation, newest ts) each time messages are put on screen, so the window can count them as seen.
    BiConsumer<String, String> onShown = (c, ts) -> { };
    String conv;
    boolean more, loading;

    public MessageList(Cx cx, Avatars avatars) {
        this.cx = cx;
        this.avatars = avatars;
        view.getStyleClass().add("msg-list");
        view.setFocusTraversable(false);
        view.setCellFactory(v -> new MsgCell(this));
        view.addEventFilter(ScrollEvent.SCROLL, e -> { if (e.getDeltaY() > 0) Platform.runLater(this::olderIfAtTop); });
        compose.setPromptText("Write a message: Enter sends, Shift+Enter starts a new line, Esc goes back to the list");
        compose.setWrapText(true);
        compose.setPrefRowCount(2);
        compose.getStyleClass().add("compose");
        title.getStyleClass().add("chat-title-text");
        title.setMinWidth(0);
        hint.getStyleClass().add("chat-hint");
        var gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        var bar = hbox().spacing(12).styleClass("chat-title").attr(b -> { b.setAlignment(Pos.CENTER_LEFT); b.setPadding(new Insets(0, 16, 0, 20));
            b.setMinHeight(49); b.setPrefHeight(49); }).nodes(fx(title), fx(gap), fx(hint));
        var box = vbox().styleClass("composer-box").nodes(fx(compose));
        var write = vbox().attr(w -> w.setPadding(new Insets(4, 20, 20, 20))).nodes(box);
        node = vbox(bar, fx(view), write).styleClass("main-pane").attr(v -> { v.setMinWidth(0); VBox.setVgrow(view, Priority.ALWAYS); }).node;
        view.setMinWidth(0);
    }

    /// Opens a conversation at its newest page, scrolled to the bottom.
    public void open(String id, String label) {
        conv = id;
        title.setText(label);
        hint.setText("loading...");
        view.getItems().clear();
        loading = true;
        fetch(id, new Object[] {"channel", id, "limit", PAGE}, (r, page) -> {
            more = r.path("has_more").asBoolean();
            view.getItems().setAll(page);
            retitle();
            toBottom();
        }, "load the messages");
    }

    /// To the newest message. Rows get their real heights only as they are drawn (wrapped lines, pictures), and one extra
    /// pass was not always enough, so it scrolls to the end again on the next two pulses and once more 150 ms later.
    void toBottom() {
        var n = view.getItems().size();
        if (n == 0) return;
        onShown.accept(conv, view.getItems().getLast().path("ts").asText());
        view.scrollTo(n - 1);
        Runnable end = () -> { if (view.lookup(".virtual-flow") instanceof VirtualFlow<?> f) f.scrollPixels(1e7); };
        Platform.runLater(() -> { end.run(); Platform.runLater(end); });
        var later = new PauseTransition(Duration.millis(150));
        later.setOnFinished(e -> end.run());
        later.play();
    }

    /// The page before the oldest one shown, put on top, keeping the message that was at the top in view.
    public void older() {
        var items = view.getItems();
        if (conv == null || !more || loading || items.isEmpty()) return;
        loading = true;
        fetch(conv, new Object[] {"channel", conv, "latest", items.getFirst().path("ts").asText(), "inclusive", false, "limit", PAGE}, (r, page) -> {
            more = r.path("has_more").asBoolean();
            items.addAll(0, page);
            view.scrollTo(page.size());
            retitle();
        }, "load older messages");
    }

    /// What arrived after the newest one shown (after a send, say), appended at the bottom.
    public void newer() {
        var items = view.getItems();
        if (conv == null || loading || items.isEmpty()) return;
        fetch(conv, new Object[] {"channel", conv, "oldest", items.getLast().path("ts").asText(), "inclusive", false, "limit", 200}, (r, page) -> {
            // a send and the socket's echo of it can both ask before either has appended: add only what is not on screen
            var last = items.isEmpty() ? "" : items.getLast().path("ts").asText();
            var fresh = page.stream().filter(m -> m.path("ts").asText().compareTo(last) > 0).toList();
            if (fresh.isEmpty()) return;
            items.addAll(fresh);
            toBottom();
            retitle();
        }, "load new messages");
    }

    void fetch(String id, Object[] kv, BiConsumer<JsonNode, List<ObjectNode>> done, String what) {
        Bg.run(() -> {
            try {
                var r = cx.api.call("conversations.history", kv);
                var page = newestLast(r);
                var names = names(page);
                Platform.runLater(() -> { loading = false; if (!id.equals(conv)) return; labels.putAll(names); done.accept(r, page); });
            } catch (RuntimeException e) {
                Platform.runLater(() -> { loading = false; hint.setText("could not " + what + ": " + e.getMessage()); });
            }
        });
    }

    static List<ObjectNode> newestLast(JsonNode r) {
        var out = new ArrayList<ObjectNode>();
        r.path("messages").forEach(m -> { if (m instanceof ObjectNode o) out.addFirst(o); });
        return out;
    }

    /// Every person and channel the page names, resolved now (background lane) so drawing never calls Slack.
    Map<String, String> names(List<ObjectNode> page) {
        var out = new HashMap<String, String>();
        for (var m : page) {
            var u = m.path("user").asText("");
            if (!u.isEmpty()) out.computeIfAbsent(u, cx::userName);
            var x = MENTION.matcher(m.path("text").asText("") + m.path("attachments").toString());
            while (x.find()) {
                if (x.group(1) != null) out.computeIfAbsent(x.group(1), cx::userName);
                else out.computeIfAbsent(x.group(2), cx::channelLabel);
            }
        }
        return out;
    }

    void retitle() {
        var n = view.getItems().size();
        hint.setText(more ? "latest " + n + " messages · F5 or scroll up for older" : n + (n == 1 ? " message" : " messages"));
    }

    String author(ObjectNode m) {
        var u = m.path("user").asText("");
        if (!u.isEmpty()) return labels.getOrDefault(u, u);
        var bot = m.path("bot_profile").path("name").asText("");
        return !bot.isEmpty() ? bot : m.path("username").asText("bot");
    }

    void olderIfAtTop() {
        if (view.lookup(".virtual-flow") instanceof VirtualFlow<?> f && f.getFirstVisibleCell() != null && f.getFirstVisibleCell().getIndex() == 0) older();
    }

    public void scroll(int pages) {
        if (view.lookup(".virtual-flow") instanceof VirtualFlow<?> f) f.scrollPixels(pages * view.getHeight() * 0.9);
        if (pages < 0) Platform.runLater(this::olderIfAtTop);
    }

    /// Moves the selected message; with nothing selected, Up starts at the newest.
    public void move(int step) {
        var n = view.getItems().size();
        if (n == 0) return;
        var at = view.getSelectionModel().getSelectedIndex();
        var i = at < 0 ? n - 1 : Math.max(0, Math.min(n - 1, at + step));
        view.getSelectionModel().select(i);
        view.scrollTo(Math.max(0, i - 2));
    }

    public String selectedText() {
        var m = view.getSelectionModel().getSelectedItem();
        return m == null ? null : MsgParts.plain(m, labels);
    }
}
