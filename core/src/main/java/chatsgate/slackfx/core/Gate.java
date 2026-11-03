package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.client.GateClient;
import cdpai.gate.client.GateDeniedException;
import cdpai.gate.client.ScopeRequest;

import module java.base;

import static chatsgate.slackfx.core.Out.M;

/// The browser through cdpgate: one flat pipe channel, tab calls carry a sessionId. Replies are matched
/// on a reader thread so every call has a real timeout (a lazily restored background tab never answers).
public final class Gate implements AutoCloseable {

    public static final String APP = "slackfx";
    public static final List<String> DOMAINS = List.of("slack.com");

    final GateClient client;
    final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    final AtomicInteger nextId = new AtomicInteger(1);
    volatile String failure;

    public Gate(List<String> profiles) {
        try {
            client = GateClient.connect(GateClient.PIPE_NAME, APP, new ScopeRequest(DOMAINS, profiles, null), null);
        } catch (GateDeniedException e) {
            throw new SlException("cdpgate: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new SlException("cannot reach cdpgate -- is cdpgate.exe running and unlocked? (" + e.getMessage() + ")", e);
        }
        Thread.ofPlatform().daemon().name("slk-gate-reader").start(this::pump);
    }

    void pump() {
        try {
            String raw;
            while ((raw = client.receive()) != null) {
                var msg = M.readTree(raw);
                if (!msg.has("id")) continue;
                var f = pending.remove(msg.path("id").asInt());
                if (f != null) f.complete(msg);
            }
        } catch (Exception e) {
            failure = String.valueOf(e.getMessage());
        }
        pending.values().forEach(f -> f.completeExceptionally(new SlException("cdpgate connection closed"
            + (failure == null ? "" : ": " + failure))));
    }

    public List<JsonNode> pageTargets() {
        var out = new ArrayList<JsonNode>();
        call(null, "Target.getTargets", M.createObjectNode()).path("targetInfos").forEach(t -> {
            if ("page".equals(t.path("type").asText())) out.add(t);
        });
        return out;
    }

    /// The Slack web app tab (it holds the token in localStorage); any slack.com tab as a fallback.
    public JsonNode appTab() {
        var tabs = pageTargets();
        return tabs.stream().filter(t -> t.path("url").asText().contains("app.slack.com")).findFirst()
            .or(() -> tabs.stream().findFirst())
            .orElseThrow(() -> new SlException("no workspace tab in the approved profile(s) -- open and log in at"
                + " https://app.slack.com in that browser profile, or name it with --profiles (cdpg profiles lists them)"));
    }

    public String attach(String targetId) {
        return call(null, "Target.attachToTarget", M.createObjectNode().put("targetId", targetId).put("flatten", true))
            .path("sessionId").asText();
    }

    public JsonNode call(String sessionId, String method, ObjectNode params) {
        var n = nextId.getAndIncrement();
        var f = new CompletableFuture<JsonNode>();
        pending.put(n, f);
        try {
            var msg = M.createObjectNode().put("id", n).put("method", method);
            msg.set("params", params);
            if (sessionId != null) msg.put("sessionId", sessionId);
            client.send(msg.toString());
            var res = f.get(20, TimeUnit.SECONDS);
            if (res.has("error")) throw new SlException(method + " failed: " + res.path("error").path("message").asText());
            return res.path("result");
        } catch (TimeoutException e) {
            throw new SlException(method + " got no answer in 20 s -- if the tab is in the background, click it once"
                + " (Vivaldi does not load restored background tabs until they are shown) and retry");
        } catch (SlException e) {
            throw e;
        } catch (Exception e) {
            throw new SlException(method + " failed", e);
        } finally {
            pending.remove(n);
        }
    }

    @Override public void close() { client.cancelPendingIo(); client.close(); }
}
