package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;
import module java.net.http;

import static chatsgate.slackfx.core.Out.M;

/// Slack's web API. Two credential shapes: a durable app user-token (xoxp-/xoxb-) sent as a plain Bearer,
/// or a browser-session xoxc- token which additionally needs the profile's `d` cookie. The cookie is sent
/// only when one was captured; a Bearer-only token carries no cookie at all.
public final class Api {

    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    final SlSession s;

    public Api(SlSession s) { this.s = s; }

    /// Methods that only read. Everything else changes something in the workspace and must be approved (Cx.write).
    static final Set<String> READS = Set.of("api.test", "auth.test", "bookmarks.list", "bots.info", "chat.getPermalink",
        "chat.scheduledMessages.list", "conversations.history", "conversations.info", "conversations.list", "conversations.members",
        "conversations.open", "conversations.replies", "dnd.info", "drafts.list", "emoji.list", "files.info", "files.list", "pins.list",
        "reactions.get", "reactions.list", "reminders.info", "reminders.list", "search.all", "search.files", "search.messages",
        "stars.list", "team.info", "usergroups.list", "usergroups.users.list", "users.conversations", "users.getPresence", "users.info",
        "users.list", "users.lookupByEmail", "users.profile.get");

    public static boolean isRead(String method) { return READS.contains(method); }

    /// A read. A method that is not a known read is refused here: it has to go through Cx.write, which asks a person first.
    public JsonNode call(String method, Map<String, ?> params) {
        if (!isRead(method)) throw new SlException(method + " changes things in the workspace, so it needs approval: it goes through a running slackfx (Cx.write)");
        return send(method, params);
    }

    /// The call itself, with no check. Only for a write that a person has approved, or one made from the slackfx window.
    public JsonNode send(String method, Map<String, ?> params) {
        if (Demo.on()) return Demo.call(method, params);
        for (var attempt = 0; ; attempt++) {
            var res = post(method, params);
            if (res.statusCode() == 429 && attempt < 3) {
                pause(res.headers().firstValue("Retry-After").map(Integer::parseInt).orElse(2));
                continue;
            }
            return check(method, res);
        }
    }

    public JsonNode call(String method, Object... kv) { return call(method, map(kv)); }

    /// Follows next_cursor, collecting `field` items from each page, up to `maxPages`.
    public List<JsonNode> pages(String method, Map<String, Object> params, String field, int maxPages) {
        var out = new ArrayList<JsonNode>();
        var p = new LinkedHashMap<>(params);
        for (var i = 0; i < maxPages; i++) {
            var r = call(method, p);
            r.path(field).forEach(out::add);
            var next = r.path("response_metadata").path("next_cursor").asText("");
            if (next.isBlank()) break;
            p.put("cursor", next);
        }
        return out;
    }

    public HttpResponse<byte[]> download(String url) {
        try {
            var b = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + s.token())
                .header("User-Agent", UA).GET();
            var ck = cookie();
            if (ck != null) b.header("Cookie", ck);
            return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new SlException("download failed: " + e.getMessage(), e);
        }
    }

    HttpResponse<String> post(String method, Map<String, ?> params) {
        var base = s.teamUrl() == null ? "https://slack.com/" : s.teamUrl();
        try {
            var b = HttpRequest.newBuilder(URI.create(base + "api/" + method))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + s.token())
                .header("Origin", "https://app.slack.com")
                .header("User-Agent", UA)
                .POST(HttpRequest.BodyPublishers.ofString(form(params)));
            var ck = cookie();
            if (ck != null) b.header("Cookie", ck);
            return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new SlException(method + " failed: " + e.getMessage(), e);
        }
    }

    JsonNode check(String method, HttpResponse<String> res) {
        try {
            var j = M.readTree(res.body());
            if (j.path("ok").asBoolean()) return j;
            var err = j.path("error").asText("unknown_error");
            throw new SlException(method + ": " + err + hint(err));
        } catch (SlException e) {
            throw e;
        } catch (Exception e) {
            throw new SlException(method + ": HTTP " + res.statusCode() + " " + Out.oneLine(res.body(), 200), e);
        }
    }

    static String hint(String err) {
        return switch (err) {
            case "invalid_auth", "not_authed", "token_revoked", "account_inactive" ->
                " -- the stored token no longer works; for the app token run 'slackfx auth --app', for a browser session log in and run 'slackfx auth'";
            case "missing_scope" -> " -- the token was not granted this scope; add it to the app's manifest and reinstall, or use a browser session";
            case "not_allowed_token_type" -> " -- the service does not accept this kind of token for this method";
            default -> "";
        };
    }

    /// The browser-session cookie, or null for a Bearer-only (xoxp-/xoxb-) token, which needs none.
    String cookie() {
        if (s.dCookie() == null) return null;
        return "d=" + s.dCookie() + (s.dsCookie() == null ? "" : "; d-s=" + s.dsCookie());
    }

    static String form(Map<String, ?> params) {
        var all = new LinkedHashMap<String, Object>();
        all.putAll(params);
        return all.entrySet().stream().filter(e -> e.getValue() != null)
            .map(e -> enc(e.getKey()) + "=" + enc(String.valueOf(e.getValue()))).collect(Collectors.joining("&"));
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    static Map<String, Object> map(Object[] kv) {
        var m = new LinkedHashMap<String, Object>();
        for (var i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    static void pause(int seconds) {
        try { Thread.sleep(seconds * 1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
