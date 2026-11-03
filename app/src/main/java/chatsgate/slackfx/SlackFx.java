package chatsgate.slackfx;

import leasegate.core.IdleTrim;
import chatsgate.slackfx.core.AppLink;
import chatsgate.slackfx.core.Cx;
import chatsgate.slackfx.core.Demo;
import chatsgate.slackfx.core.Store;

import module java.base;
import module java.net.http;

/// slackfx: this machine's resident, low-RAM Slack client. It holds the durable xoxp- token (the active account), serves
/// the CLI on loopback with an approval for every write, and shows the chat window from the tray. Starting it again
/// while it runs only brings the window up. `--background` starts it in the tray without opening the window.
public final class SlackFx {

    /// Made on the JavaFX thread the first time it is opened, so a tray-only slackfx never loads JavaFX for it.
    static volatile ChatWindow window;

    public static void main(String[] args) throws Exception {
        var started = ProcessHandle.current().info().startInstant().orElse(Instant.now());
        Log.info("main reached " + Duration.between(started, Instant.now()).toMillis() + " ms after the process started (launcher + JVM)");
        if (showRunning()) return;
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> Log.error("uncaught in " + t.getName(), ex));        var cx = Demo.on() ? Cx.demo() : Cx.open(null); // the active account: the durable xoxp- session by default
        Log.info("slackfx starting as " + cx.session.label() + " (" + cx.session.userName() + " / " + cx.session.teamName() + ")");
        Runnable open = () -> Fx.run(() -> {
            Log.info("opening the window (JavaFX running)");
            if (window == null) window = new ChatWindow(cx);
            window.show();
        });
        var tray = Tray.start(cx.session.label(), open, () -> System.exit(0));
        new Feed(cx, () -> window, tray, id -> Fx.run(() -> {
            if (window == null) window = new ChatWindow(cx);
            window.openConversation(id);
        })).start();
        var server = new Server(cx, open, () -> {
            var w = window;
            if (w == null || w.frame == null) return "no window has been opened yet";
            return Fx.call(() -> Snap.png(w.frame.stage.getScene(), Store.home().resolve("run").resolve("slackfx-window.png"))).toString();
        });
        server.demoNotify = () -> tray.notify("Test sender in #general", "This is a sample slackfx notification. Click it within 30 s to open the window.", open);
        if (Demo.on()) server.openConversation = id -> Fx.run(() -> { if (window == null) window = new ChatWindow(cx); window.openConversation(id); });
        server.start();
        IdleTrim.start("slackfx", () -> later(server.lastActivity, window == null ? Instant.EPOCH : window.lastActivity), Duration.ofMinutes(2));
        if (!Demo.on()) UiUpdates.start();
        Log.info("slackfx running");
        if (!List.of(args).contains("--background")) open.run();
        Thread.currentThread().join();
    }

    static Instant later(Instant a, Instant b) { return a.isAfter(b) ? a : b; }

    /// True when another slackfx is already running and was asked to show its window.
    static boolean showRunning() {
        var info = AppLink.live();
        if (info == null) return false;
        try {
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + info.path("port").asInt() + "/show"))
                .header("X-Slk-Token", info.path("token").asText()).timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());
            Log.info("another slackfx is running; asked it to show and exiting");
            return true;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
