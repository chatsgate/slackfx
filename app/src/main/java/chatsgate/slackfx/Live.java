package chatsgate.slackfx;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;
import module java.net.http;

import static chatsgate.slackfx.core.Out.M;

/// Slack pushing events to us over a WebSocket (Socket Mode), the way the desktop app hears about new messages instead of
/// asking. It needs the app-level token (xapp-, scope connections:write): apps.connections.open hands back a one-off
/// address, Slack says "hello", then sends each event in an envelope that must be acknowledged within 3 seconds. Slack
/// closes the socket now and then ("disconnect"); the loop opens a new one, waiting longer after each failure.
public final class Live implements WebSocket.Listener {

    static final HttpClient HTTP = HttpClient.newHttpClient();

    final String appToken;
    final Consumer<JsonNode> onEvent;
    final StringBuilder buf = new StringBuilder();
    volatile boolean connected;
    volatile Instant lastEvent = Instant.EPOCH;
    volatile CompletableFuture<Void> closed;

    /// `onEvent` gets each event (payload.event) off the FX thread.
    public Live(String appToken, Consumer<JsonNode> onEvent) { this.appToken = appToken; this.onEvent = onEvent; }

    public void start() { Thread.ofVirtual().name("slack-socket").start(this::loop); }

    void loop() {
        var wait = 2;
        while (true) {
            try {
                closed = new CompletableFuture<>();
                HTTP.newWebSocketBuilder().buildAsync(URI.create(address()), this).join();
                wait = 2;
                closed.join();
            } catch (Exception e) {
                Log.warn("socket: " + e.getMessage());
            }
            connected = false;
            try { Thread.sleep(Duration.ofSeconds(wait)); } catch (InterruptedException e) { return; }
            wait = Math.min(60, wait * 2);
        }
    }

    /// A fresh socket address from Slack (each is good for one connection).
    String address() throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create("https://slack.com/api/apps.connections.open"))
            .header("Authorization", "Bearer " + appToken).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(20)).build();
        var r = M.readTree(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body());
        if (!r.path("ok").asBoolean()) throw new IOException("apps.connections.open: " + r.path("error").asText());
        return r.path("url").asText();
    }

    @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        buf.append(data);
        if (last) {
            var s = buf.toString();
            buf.setLength(0);
            handle(ws, s);
        }
        ws.request(1);
        return null;
    }

    void handle(WebSocket ws, String s) {
        try {
            var env = M.readTree(s);
            switch (env.path("type").asText()) {
                case "hello" -> { connected = true; Log.info("socket connected (" + env.path("num_connections").asInt() + " connection(s))"); }
                case "disconnect" -> { Log.info("socket: the service asks to reconnect (" + env.path("reason").asText() + ")"); ws.sendClose(WebSocket.NORMAL_CLOSURE, ""); }
                case "events_api" -> {
                    ws.sendText(M.createObjectNode().put("envelope_id", env.path("envelope_id").asText()).toString(), true);
                    lastEvent = Instant.now();
                    onEvent.accept(env.path("payload").path("event"));
                }
                default -> { if (env.has("envelope_id")) ws.sendText(M.createObjectNode().put("envelope_id", env.path("envelope_id").asText()).toString(), true); }
            }
        } catch (Exception e) {
            Log.error("socket message not handled", e);
        }
    }

    @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
        Log.info("socket closed (" + code + (reason.isBlank() ? "" : ", " + reason) + ")");
        connected = false;
        if (closed != null) closed.complete(null);
        return null;
    }

    @Override public void onError(WebSocket ws, Throwable error) {
        Log.warn("socket error: " + error);
        connected = false;
        if (closed != null) closed.complete(null);
    }
}
