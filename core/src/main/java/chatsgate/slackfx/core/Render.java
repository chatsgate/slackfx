package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;

/// Slack message JSON as readable lines: names for ids, links unwrapped, one block per message.
public final class Render {

    static final Pattern
        USER = Pattern.compile("<@([UW][A-Z0-9]+)(?:\\|[^>]*)?>"),
        CHAN = Pattern.compile("<#([CG][A-Z0-9]+)(?:\\|([^>]*))?>"),
        GROUP = Pattern.compile("<!subteam\\^[A-Z0-9]+\\|(@[^>]+)>"),
        SPECIAL = Pattern.compile("<!([a-z]+)(?:\\|[^>]*)?>"),
        LABELLED = Pattern.compile("<((?:https?://|mailto:)[^|>]+)\\|([^>]+)>"),
        BARE = Pattern.compile("<((?:https?://|mailto:)[^>]+)>");

    private Render() { }

    public static String clean(Cx cx, String text) {
        var t = USER.matcher(text).replaceAll(m -> Matcher.quoteReplacement("@" + cx.userName(m.group(1))));
        t = CHAN.matcher(t).replaceAll(m -> Matcher.quoteReplacement(m.group(2) != null ? "#" + m.group(2) : cx.channelLabel(m.group(1))));
        t = GROUP.matcher(t).replaceAll("$1");
        t = SPECIAL.matcher(t).replaceAll("@$1");
        t = LABELLED.matcher(t).replaceAll(m -> Matcher.quoteReplacement(m.group(2) + " (" + m.group(1) + ")"));
        t = BARE.matcher(t).replaceAll("$1");
        return t.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    public static String who(Cx cx, JsonNode m) {
        if (m.hasNonNull("user")) return "@" + cx.userName(m.path("user").asText());
        var bot = m.path("bot_profile").path("name").asText(m.path("username").asText(""));
        return bot.isBlank() ? "?" : bot + " (bot)";
    }

    public static String message(Cx cx, JsonNode m) {
        var ts = m.path("ts").asText();
        var head = ts + "  " + Ref.show(ts) + "  " + who(cx, m) + ":";
        var body = clean(cx, text(m)).replace("\n", "\n    ");
        return head + " " + body + extras(m);
    }

    /// The message text; bots often leave it empty and put the content in attachments (their fallback line) or blocks.
    public static String text(JsonNode m) {
        var t = m.path("text").asText("");
        if (!t.isBlank()) return t;
        var sb = new StringBuilder();
        m.path("attachments").forEach(a -> sb.append(sb.isEmpty() ? "" : "\n").append(a.path("fallback").asText(a.path("text").asText(""))));
        if (sb.isEmpty()) m.path("blocks").forEach(b -> b.path("text").path("text").asText("").lines().findFirst().ifPresent(l -> sb.append(l).append(" ")));
        return sb.toString().strip();
    }

    /// The message, then each of its thread replies (when fetched) indented under it.
    public static String withReplies(Cx cx, JsonNode m) {
        var sb = new StringBuilder(message(cx, m));
        m.path("replies_full").forEach(r -> sb.append("\n  ↳ ").append(message(cx, r).replace("\n", "\n  ")));
        return sb.toString();
    }

    static String extras(JsonNode m) {
        var sb = new StringBuilder();
        m.path("files").forEach(f -> sb.append("  [file: ").append(f.path("name").asText(f.path("title").asText("?"))).append(" ").append(f.path("id").asText()).append("]"));
        m.path("reactions").forEach(r -> sb.append("  :").append(r.path("name").asText()).append(":x").append(r.path("count").asInt()));
        if (m.path("reply_count").asInt() > 0) sb.append("  [").append(m.path("reply_count").asInt()).append(" replies]");
        return sb.toString();
    }
}
