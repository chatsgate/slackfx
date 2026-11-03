package chatsgate.slackfx;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import chatsgate.slackfx.core.Api;
import chatsgate.slackfx.core.AppLink;
import chatsgate.slackfx.core.Cx;
import chatsgate.slackfx.core.Demo;

import module java.base;

import static chatsgate.slackfx.core.Out.M;

/// The loopback door slk talks through: 127.0.0.1 only, and every call must carry the token from
/// ~/chatsgate/slackfx/run/app.json. Reads (status) and, from Stage C, gated writes.
public final class Server {


    final Cx cx;
    final Runnable show;
    final Callable<String> snapshot;
    final String token = HexFormat.of().formatHex(new SecureRandom().generateSeed(16));
    final Instant started = Instant.now();
    volatile Instant lastActivity = Instant.now();
    /// Raises a sample notification through the real path, to see how one looks without waiting for a message.
    Runnable demoNotify = () -> { };
    java.util.function.Consumer<String> openConversation;
    HttpServer http;

    public Server(Cx cx, Runnable show, Callable<String> snapshot) { this.cx = cx; this.show = show; this.snapshot = snapshot; }

    public void start() throws IOException {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/status", ex -> guarded(ex, () -> M.createObjectNode()
            .put("pid", ProcessHandle.current().pid()).put("startedAt", started.toString())
            .put("account", cx.session.label()).put("user", cx.session.userName())
            .put("team", cx.session.teamName()).toString()));
        http.createContext("/write", ex -> guarded(ex, () -> write(ex)));
        http.createContext("/show", ex -> guarded(ex, () -> { show.run(); return "shown"; }));
        http.createContext("/snapshot", ex -> guarded(ex, snapshot::call));
        http.createContext("/notify-test", ex -> guarded(ex, () -> { demoNotify.run(); return "notified"; }));
        if (Demo.on()) http.createContext("/open", ex -> guarded(ex, () -> { openConversation.accept(ex.getRequestURI().getQuery().replaceFirst("^id=", "")); return "opened"; }));
        http.createContext("/stop", ex -> guarded(ex, () -> { Thread.ofVirtual().start(this::stop); return "stopping"; }));
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        AppLink.write(http.getAddress().getPort(), token);
        Runtime.getRuntime().addShutdownHook(new Thread(AppLink::delete));
        Log.info("listening on 127.0.0.1:" + http.getAddress().getPort() + " as " + cx.session.label());
    }

    interface Body { String get() throws Exception; }

    /// A gated write that the human refused in the approval window -> 403 (distinct from a server error).
    static final class Denied extends RuntimeException { }

    void guarded(HttpExchange ex, Body body) throws IOException {
        lastActivity = Instant.now();
        int code = 200;
        String out;
        if (!tokenOk(ex.getRequestHeaders().getFirst("X-Slk-Token"))) { code = 403; out = "forbidden"; }
        else try { out = body.get(); }
        catch (Denied d) { code = 403; out = "denied"; }
        catch (Exception e) { code = 500; out = String.valueOf(e.getMessage()); }
        var bytes = out.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (var os = ex.getResponseBody()) { os.write(bytes); }
    }

    /// Constant-time token check, so a wrong token leaks nothing through timing.
    boolean tokenOk(String got) {
        return got != null && MessageDigest.isEqual(got.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    /// A CLI write: {method, params}. Allowed methods only, a branded F2 approval, then performed with the app's
    /// own token. The human sees exactly what will happen (channel resolved to its name, the text in full).
    String write(HttpExchange ex) throws IOException {
        var body = M.readTree(ex.getRequestBody().readAllBytes());
        var method = body.path("method").asText();
        if (Api.isRead(method)) throw new IllegalArgumentException(method + " only reads: it needs no approval, call it directly");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = M.convertValue(body.path("params"), Map.class);
        Log.info("write request: " + method + " -> " + params.getOrDefault("channel", "?"));
        if (!Confirm.ask("slackfx - approve this action", describe(method, params), "Approve", "Deny")) {
            Log.info("write denied by user: " + method);
            throw new Denied();
        }
        var r = cx.api.send(method, params);
        Log.info("write approved and done: " + method);
        return r.toString();
    }

    /// A plain, human sentence for the approval window. Channel ids become #names/@people; the message text shows in full.
    String describe(String method, Map<String, Object> p) {
        // the name lookup touches Cx's cache, which the window also uses, so it runs on the one background lane
        var ch = p.containsKey("channel") ? Bg.call(() -> cx.channelLabel(String.valueOf(p.get("channel")))) : "?";
        return switch (method) {
            case "chat.postMessage" -> "Send to " + ch + (p.containsKey("thread_ts") ? " (thread reply)" : "")
                + ", as " + cx.session.userName() + ":\n\n" + p.get("text");
            case "chat.update" -> "Edit your message in " + ch + " (ts " + p.get("ts") + ") to:\n\n" + p.get("text");
            case "chat.delete" -> "Delete your message in " + ch + " (ts " + p.get("ts") + ")";
            case "reactions.add" -> "Add reaction :" + p.get("name") + ": in " + ch + " (ts " + p.get("timestamp") + ")";
            case "reactions.remove" -> "Remove reaction :" + p.get("name") + ": in " + ch + " (ts " + p.get("timestamp") + ")";
            case "conversations.mark" -> "Mark " + ch + " as read, up to ts " + p.get("ts") + " (this changes your unread state)";
            case "chat.scheduleMessage", "drafts.create" -> "Schedule a message in " + (p.containsKey("destinations") ? "the destination below" : ch)
                + " (it is sent later, as " + cx.session.userName() + "):\n\n" + p;
            case "drafts.delete" -> "Cancel a scheduled message (draft " + p.get("draft_id") + ")";
            default -> "Call " + method + " (it changes things in the workspace) with:\n\n" + p;
        };
    }

    void stop() {
        Log.info("stopping on request");
        http.stop(0);
        AppLink.delete();
        System.exit(0);
    }
}
