package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import module java.base;

import static chatsgate.slackfx.core.Out.M;

/// A made-up workspace for screenshots and for trying the window without a Slack login: `slackfx --demo`. Every name,
/// channel and message is invented (demo.json). It answers the few API methods the window uses and touches nothing else:
/// no token, no network, and its own state folder.
public final class Demo {

    static final JsonNode DATA = load();
    static final long NOW = System.currentTimeMillis() / 1000;

    private Demo() { }

    public static boolean on() { return Boolean.getBoolean("slackfx.demo"); }

    static JsonNode load() {
        try (var in = Demo.class.getResourceAsStream("demo.json")) {
            return M.readTree(in);
        } catch (IOException e) {
            throw new SlException("cannot read demo.json", e);
        }
    }

    public static SlSession session() {
        var t = DATA.path("team");
        return new SlSession().label("demo").token("demo").teamId(t.path("id").asText()).teamName(t.path("name").asText())
            .teamUrl(t.path("url").asText()).userId(DATA.path("me").asText()).userName(user(DATA.path("me").asText()).path("name").asText());
    }

    static String ts(long agoMinutes) { return (NOW - agoMinutes * 60) + "." + "%06d".formatted(agoMinutes); }

    static JsonNode user(String id) {
        for (var u : DATA.path("users")) if (u.path("id").asText().equals(id)) return u;
        throw new SlException("demo: no user " + id);
    }

    static JsonNode conversation(String id) {
        for (var c : DATA.path("conversations")) if (c.path("id").asText().equals(id)) return c;
        throw new SlException("demo: no conversation " + id);
    }

    static ObjectNode ok() { return M.createObjectNode().put("ok", true); }

    public static JsonNode call(String method, Map<String, ?> p) {
        return switch (method) {
            case "auth.test" -> ok().put("user", session().userName()).put("user_id", session().userId())
                .put("team", session().teamName()).put("team_id", session().teamId()).put("url", session().teamUrl());
            case "users.list" -> ok().set("members", users());
            case "users.info" -> ok().set("user", user(String.valueOf(p.get("user"))));
            case "users.conversations" -> ok().set("channels", conversations());
            case "conversations.info" -> ok().set("channel", info(String.valueOf(p.get("channel"))));
            case "conversations.history" -> history(String.valueOf(p.get("channel")), p.get("limit"));
            case "chat.postMessage" -> ok().put("ts", ts(0)).put("channel", String.valueOf(p.get("channel")));
            default -> throw new SlException("demo: " + method + " is not available in the demo workspace");
        };
    }

    static ArrayNode users() {
        var out = M.createArrayNode();
        DATA.path("users").forEach(u -> out.add(M.createObjectNode().put("id", u.path("id").asText()).put("name", u.path("name").asText())
            .put("real_name", u.path("real_name").asText()).set("profile", M.createObjectNode().put("real_name", u.path("real_name").asText()))));
        return out;
    }

    static ArrayNode conversations() {
        var out = M.createArrayNode();
        DATA.path("conversations").forEach(c -> {
            var kind = c.path("kind").asText();
            var o = M.createObjectNode().put("id", c.path("id").asText()).put("updated", (NOW - c.path("ago").asLong() * 60) * 1000);
            switch (kind) {
                case "im" -> o.put("is_im", true).put("user", c.path("user").asText());
                case "mpim" -> o.put("is_mpim", true).put("name", c.path("name").asText());
                default -> o.put("is_channel", true).put("is_private", kind.equals("private")).put("name", c.path("name").asText());
            }
            out.add(o);
        });
        return out;
    }

    static ObjectNode info(String id) {
        var c = conversation(id);
        var o = M.createObjectNode().put("id", id).put("name", c.path("name").asText(""));
        if (c.path("kind").asText().equals("im")) o.put("is_im", true).put("user", c.path("user").asText());
        var unread = c.path("unread").asInt(0);
        o.put("last_read", unread > 0 ? "1.000000" : ts(c.path("ago").asLong())).put("unread_count_display", unread);
        return o;
    }

    static JsonNode history(String id, Object limit) {
        var all = DATA.path("messages").path(id);
        var max = limit == null ? 50 : Integer.parseInt(String.valueOf(limit));
        var list = new ArrayList<JsonNode>();
        all.forEach(list::add);
        list.sort(Comparator.comparingLong((JsonNode m) -> m.path("ago").asLong()));
        var out = M.createArrayNode();
        list.stream().limit(max).forEach(m -> out.add(message(m)));
        return ok().put("has_more", false).set("messages", out);
    }

    static ObjectNode message(JsonNode m) {
        var o = M.createObjectNode().put("type", "message").put("ts", ts(m.path("ago").asLong()));
        if (m.has("user")) o.put("user", m.path("user").asText());
        if (m.has("bot")) o.put("bot_id", "B0DEMO001").set("bot_profile", M.createObjectNode().put("name", m.path("bot").asText()));
        if (m.has("text")) o.put("text", m.path("text").asText());
        if (m.has("reactions")) o.set("reactions", m.path("reactions"));
        if (m.has("reply_count")) o.put("reply_count", m.path("reply_count").asInt());
        if (m.path("edited").asBoolean()) o.set("edited", M.createObjectNode().put("user", m.path("user").asText()).put("ts", ts(m.path("ago").asLong() - 1)));
        if (m.has("files")) {
            var files = M.createArrayNode();
            m.path("files").forEach(f -> files.add(M.createObjectNode().put("name", f.asText())));
            o.set("files", files);
        }
        if (m.has("attachment")) {
            var a = m.path("attachment");
            o.set("attachments", M.createArrayNode().add(M.createObjectNode().put("title", a.path("title").asText())
                .put("title_link", a.path("link").asText()).put("text", a.path("text").asText())));
        }
        return o;
    }
}
