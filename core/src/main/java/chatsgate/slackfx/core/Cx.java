package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;

/// One account's working context: the api plus a small persistent id<->name cache so output reads as people and channels.
public final class Cx {

    static final Pattern CHANNEL_ID = Pattern.compile("^[CGD][A-Z0-9]{8,}$"), USER_ID = Pattern.compile("^[UW][A-Z0-9]{8,}$");

    public final SlSession session;
    public final Api api;
    final Cache cache;

    Cx(SlSession session) {
        this.session = session;
        this.api = new Api(session);
        this.cache = Store.loadCache(session.teamId());
    }

    /// The made-up workspace of `slackfx --demo`.
    public static Cx demo() { return new Cx(Demo.session()); }

    public static Cx open(String account) {
        var s = Store.loadSessions().resolve(account).orElseThrow(() -> new SlException(
            "no saved account -- log in to your workspace in the browser, then run 'slackfx auth'"));
        return new Cx(s);
    }

    /// True when a slackfx app is running: writes will go through it for a branded approval.
    public boolean gated() { return AppLink.live() != null; }

    /// A write (post/edit/delete/react). If slackfx is running it approves and performs it with its own token;
    /// otherwise it is a direct API call. Reads never come here -- they use `api.call` straight.
    public JsonNode write(String method, Object... kv) { return write(method, Api.map(kv)); }

    public JsonNode write(String method, Map<String, Object> params) {
        var app = AppLink.live();
        if (app == null && Store.requireApproval())
            throw new SlException("writes need a person to approve them: start slackfx (no arguments), then run this again."
                + " (\"require_approval\": false in app-config.json lets writes go straight through.)");
        if (app == null) return api.send(method, params);
        Out.err("waiting for approval in the slackfx window (F2 to approve)...");
        return GateClient.write(app, method, params);
    }

    public static String display(JsonNode u) {
        var p = u.path("profile");
        for (var v : new String[] {p.path("display_name").asText(""), p.path("real_name").asText(""),
            u.path("real_name").asText(""), u.path("name").asText("")}) if (!v.isBlank()) return v;
        return u.path("id").asText();
    }

    public String userName(String id) {
        var hit = cache.users().get(id);
        if (hit != null) return hit;
        try {
            var name = display(api.call("users.info", "user", id).path("user"));
            remember(id, name, true);
            return name;
        } catch (SlException e) {
            return id;
        }
    }

    public String channelLabel(String id) {
        var hit = cache.channels().get(id);
        if (hit != null) return hit;
        try {
            var c = api.call("conversations.info", "channel", id).path("channel");
            var label = c.path("is_im").asBoolean() ? "@" + userName(c.path("user").asText()) : "#" + c.path("name").asText(id);
            remember(id, label, false);
            return label;
        } catch (SlException e) {
            return id;
        }
    }

    /// A channel id, from an id, #name, name, @person or a message permalink.
    public String channelId(String ref) {
        var r = ref.trim();
        var link = Ref.PERMALINK.matcher(r);
        if (link.find()) return link.group(1);
        if (CHANNEL_ID.matcher(r).matches()) return r;
        if (r.startsWith("@") || USER_ID.matcher(r).matches())
            return api.call("conversations.open", "users", userId(r)).path("channel").path("id").asText();
        var name = r.startsWith("#") ? r.substring(1) : r;
        var found = find(name);
        if (found == null) { refreshChannels(false); found = find(name); }
        if (found == null) { refreshChannels(true); found = find(name); }
        if (found == null) throw new SlException("no channel named '" + name + "' (private channels you are not in cannot be seen)");
        return found;
    }

    public String userId(String ref) {
        var r = ref.startsWith("@") ? ref.substring(1) : ref;
        if (USER_ID.matcher(r).matches()) return r;
        var id = lookupUser(r);
        if (id == null) { scanUsers(); id = lookupUser(r); }
        if (id == null) throw new SlException("no person matching '" + r + "'");
        return id;
    }

    /// Exact display name or handle first, then a unique partial match on either.
    String lookupUser(String name) {
        var n = name.toLowerCase();
        var exact = cache.users().keySet().stream().filter(id -> cache.users().get(id).equalsIgnoreCase(name)
            || n.equals(cache.handles().getOrDefault(id, ""))).toList();
        if (exact.size() == 1) return exact.getFirst();
        var hits = exact.isEmpty() ? cache.users().keySet().stream().filter(id -> cache.users().get(id).toLowerCase().contains(n)
            || cache.handles().getOrDefault(id, "").contains(n)).toList() : exact;
        if (hits.size() == 1) return hits.getFirst();
        if (hits.size() > 1) throw new SlException("'" + name + "' matches several people: "
            + hits.stream().map(id -> id + " " + cache.users().get(id)).toList());
        return null;
    }

    String find(String name) {
        return cache.channels().entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase("#" + name))
            .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    void refreshChannels(boolean workspaceWide) {
        var p = new LinkedHashMap<String, Object>(Map.of("exclude_archived", true, "limit", 1000,
            "types", workspaceWide ? "public_channel" : "public_channel,private_channel"));
        api.pages(workspaceWide ? "conversations.list" : "users.conversations", p, "channels", 40)
            .forEach(c -> cache.channels().put(c.path("id").asText(), "#" + c.path("name").asText()));
        Store.saveCache(session.teamId(), cache);
    }

    void scanUsers() {
        api.pages("users.list", new LinkedHashMap<>(Map.of("limit", 1000)), "members", 40)
            .forEach(u -> {
                cache.users().put(u.path("id").asText(), display(u));
                cache.handles().put(u.path("id").asText(), u.path("name").asText("").toLowerCase());
            });
        Store.saveCache(session.teamId(), cache);
    }

    void remember(String id, String name, boolean user) {
        (user ? cache.users() : cache.channels()).put(id, name);
        Store.saveCache(session.teamId(), cache);
    }
}
