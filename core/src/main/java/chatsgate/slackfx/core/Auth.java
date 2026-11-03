package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;

import static chatsgate.slackfx.core.Out.M;

public final class Auth {

    static final String READ_CONFIG = "localStorage.getItem('localConfig_v2')||localStorage.getItem('localConfig_v3')||''";

    private Auth() { }

    /// Reads every workspace the logged-in Slack tab knows (token from localStorage, d cookie from the profile's jar),
    /// proves each with auth.test, and returns the sessions that work. `only` filters by name/domain/id fragment.
    public static List<SlSession> capture(List<String> profiles, String only) {
        String config, page;
        Map<String, String> cookies;
        try (var gate = new Gate(profiles)) {
            var tab = gate.appTab();
            page = tab.path("url").asText();
            var sid = gate.attach(tab.path("targetId").asText());
            config = gate.call(sid, "Runtime.evaluate", M.createObjectNode()
                .put("expression", READ_CONFIG).put("returnByValue", true)).path("result").path("value").asText("");
            var p = M.createObjectNode();
            p.putArray("urls").add("https://app.slack.com/").add("https://slack.com/");
            cookies = cookies(gate.call(sid, "Network.getCookies", p));
        }
        if (config.isBlank()) throw new SlException("the tab holds no workspace config -- log in at https://app.slack.com in that profile");
        if (!cookies.containsKey("d")) throw new SlException("no 'd' cookie for slack.com in that profile -- log in there first");
        return sessions(config, cookies, page, only);
    }

    static List<SlSession> sessions(String config, Map<String, String> cookies, String page, String only) {
        var out = new ArrayList<SlSession>();
        try {
            M.readTree(config).path("teams").properties().forEach(e -> {
                var t = e.getValue();
                var s = new SlSession().teamId(e.getKey()).teamName(Out.text(t, "name")).teamUrl(Out.text(t, "url"))
                    .token(Out.text(t, "token")).dCookie(cookies.get("d")).dsCookie(cookies.get("d-s"))
                    .capturedFrom(page).capturedAt(Instant.now().getEpochSecond());
                if (s.token().isBlank() || !matches(s, only)) return;
                try {
                    out.add(identify(s));
                } catch (SlException ex) {
                    Out.err("skipped " + s.teamName() + " (" + s.teamId() + "): " + ex.getMessage());
                }
            });
        } catch (Exception e) {
            throw new SlException("cannot read the workspace config", e);
        }
        if (out.isEmpty()) throw new SlException("no usable workspace session found in that profile");
        return out;
    }

    static boolean matches(SlSession s, String only) {
        if (only == null) return true;
        var f = only.toLowerCase();
        return s.teamId().toLowerCase().contains(f) || s.domain().toLowerCase().contains(f)
            || (s.teamName() != null && s.teamName().toLowerCase().contains(f));
    }

    /// The durable app user-token (xoxp-) saved by the one-time OAuth install, from app-config.json.
    /// No browser, no cookie, no cdpgate: a Bearer token that, with rotation disabled, does not expire.
    public static SlSession fromAppToken() {
        var cfg = Store.appConfig();
        var token = cfg.path("user_token").asText("");
        if (token.isBlank()) throw new SlException("no durable user_token in " + Store.appConfigPath()
            + " -- install the app once (https://api.slack.com/apps/<id>/oauth) and save its xoxp- user token there");
        var s = new SlSession().token(token)
            .teamId(cfg.path("team_id").asText(null)).teamName(cfg.path("team_name").asText(null))
            .capturedFrom("app-config.json (xoxp- durable token)").capturedAt(Instant.now().getEpochSecond());
        return identify(s);
    }

    public static SlSession identify(SlSession s) {
        var r = new Api(s).call("auth.test");
        var url = r.path("url").asText(null);
        return s.userId(r.path("user_id").asText()).userName(r.path("user").asText())
            .teamName(r.path("team").asText(s.teamName())).teamUrl(url != null ? url : s.teamUrl());
    }

    static Map<String, String> cookies(JsonNode res) {
        var out = new LinkedHashMap<String, String>();
        res.path("cookies").forEach(c -> out.put(c.path("name").asText(), c.path("value").asText()));
        return out;
    }
}
