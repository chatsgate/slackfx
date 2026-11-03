package chatsgate.slackfx;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import chatsgate.slackfx.core.Cx;
import chatsgate.slackfx.core.Store;

import module java.base;

/// Keeps slackfx current while it runs, window open or not. With the app-level token it listens on Slack's socket (see
/// Live); without one it asks for the open conversation's new messages every five seconds instead. A new message from
/// someone else in a direct or group message, or one that mentions you, raises a notification unless you are looking at
/// that very conversation. Clicking the notification opens it.
public final class Feed {

    final Cx cx;
    final Supplier<ChatWindow> window;
    final Tray tray;
    final Consumer<String> openConversation;
    Live live;

    public Feed(Cx cx, Supplier<ChatWindow> window, Tray tray, Consumer<String> openConversation) {
        this.cx = cx;
        this.window = window;
        this.tray = tray;
        this.openConversation = openConversation;
    }

    public void start() {
        var app = Store.appConfig().path("app_token").asText("");
        if (app.startsWith("xapp-")) { live = new Live(app, this::event); live.start(); }
        else Log.info("no app-level token (app_token in app-config.json): polling the open conversation every 5 s instead");
        Thread.ofVirtual().name("poll").start(this::poll);
    }

    /// Without a live socket: the open conversation's new messages every five seconds, while the window is open.
    void poll() {
        while (true) {
            try { Thread.sleep(Duration.ofSeconds(5)); } catch (InterruptedException e) { return; }
            var w = window.get();
            if ((live != null && live.connected) || w == null) continue;
            Platform.runLater(() -> { if (w.frame != null && w.frame.stage.isShowing()) w.messages.newer(); });
        }
    }

    void event(JsonNode e) {
        var type = e.path("type").asText();
        if (!type.equals("message") && !type.startsWith("reaction_")) return;
        var conv = type.equals("message") ? e.path("channel").asText() : e.path("item").path("channel").asText();
        Log.info("live: " + type + (e.path("subtype").asText("").isEmpty() ? "" : "/" + e.path("subtype").asText()) + " in " + conv);
        // until the window has been opened JavaFX is not running at all (that is what keeps tray-only slackfx small), so
        // only the notification is raised; touching the FX thread then fails with "Toolkit not initialized" (2026-10-27)
        var w = window.get();
        if (w == null) { if (notifies(e)) notifyAbout(e, conv); return; }
        Platform.runLater(() -> {
            w.arrived(e, conv);
            if (notifies(e) && !w.watching(conv)) notifyAbout(e, conv);
        });
    }

    /// A new message: not a reply inside a thread, not an edit, deletion or join notice.
    static boolean isNewMessage(JsonNode e) {
        if (!e.path("type").asText().equals("message")) return false;
        var sub = e.path("subtype").asText("");
        if (!sub.isEmpty() && !Set.of("bot_message", "thread_broadcast", "file_share", "me_message").contains(sub)) return false;
        var thread = e.path("thread_ts").asText("");
        return thread.isEmpty() || thread.equals(e.path("ts").asText()) || sub.equals("thread_broadcast");
    }

    /// Slack's default: every direct and group message, and anything that mentions you by name.
    boolean notifies(JsonNode e) {
        if (!isNewMessage(e) || cx.session.userId().equals(e.path("user").asText())) return false;
        var kind = e.path("channel_type").asText();
        return kind.equals("im") || kind.equals("mpim") || e.path("text").asText("").contains("<@" + cx.session.userId() + ">");
    }

    /// Who, where, and the start of the text; names are looked up on the background lane, never on the FX thread.
    void notifyAbout(JsonNode e, String conv) {
        Sound.chime();
        Bg.run(() -> {
            var user = e.path("user").asText("");
            var who = user.isEmpty() ? e.path("username").asText("app") : cx.userName(user);
            var where = cx.channelLabel(conv);
            var labels = new HashMap<String, String>();
            var m = MessageList.MENTION.matcher(e.path("text").asText(""));
            while (m.find()) if (m.group(1) != null) labels.computeIfAbsent(m.group(1), cx::userName);
            var text = MsgParts.emojify(Mrkdwn.plain(e.path("text").asText(""), labels)).replace('\n', ' ');
            tray.notify(where.startsWith("@") ? who : who + " in " + where, text.length() > 180 ? text.substring(0, 180) + "..." : text,
                () -> openConversation.accept(conv));
        });
    }
}
