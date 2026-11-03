package chatsgate.slackfx;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;

/// What hangs off a Slack message besides its own text, read out of the message object: attachments (as mrkdwn, so their
/// links stay links), files, the thread's reply count, and reactions. Also the message as plain text, for copying.
public final class MsgParts {

    /// The emoji Slack names most often, as the glyphs it draws. Anything else stays as its :name:.
    static final Map<String, String> EMOJI = Map.ofEntries(Map.entry("+1", "👍"), Map.entry("thumbsup", "👍"), Map.entry("-1", "👎"),
        Map.entry("heart", "❤️"), Map.entry("joy", "😂"), Map.entry("pray", "🙏"), Map.entry("fire", "🔥"), Map.entry("eyes", "👀"),
        Map.entry("ok_hand", "👌"), Map.entry("white_check_mark", "✅"), Map.entry("heavy_check_mark", "✔️"), Map.entry("raised_hands", "🙌"),
        Map.entry("clap", "👏"), Map.entry("smile", "😄"), Map.entry("slightly_smiling_face", "🙂"), Map.entry("100", "💯"), Map.entry("tada", "🎉"),
        Map.entry("rocket", "🚀"), Map.entry("thinking_face", "🤔"), Map.entry("star", "⭐"), Map.entry("bulb", "💡"), Map.entry("warning", "⚠️"),
        Map.entry("x", "❌"), Map.entry("wave", "👋"), Map.entry("muscle", "💪"), Map.entry("blush", "😊"), Map.entry("sob", "😭"),
        Map.entry("grinning", "😀"), Map.entry("heart_eyes", "😍"), Map.entry("pushpin", "📌"), Map.entry("memo", "📝"), Map.entry("lotus", "🪷"),
        Map.entry("clipboard", "📋"), Map.entry("calendar", "📅"), Map.entry("date", "📅"), Map.entry("link", "🔗"), Map.entry("page_facing_up", "📄"),
        Map.entry("bell", "🔔"), Map.entry("hourglass", "⌛"), Map.entry("zap", "⚡"), Map.entry("point_right", "👉"), Map.entry("arrow_right", "➡️"),
        Map.entry("information_source", "ℹ️"), Map.entry("exclamation", "❗"), Map.entry("question", "❓"), Map.entry("sparkles", "✨"),
        Map.entry("handshake", "🤝"), Map.entry("red_circle", "🔴"), Map.entry("large_green_circle", "🟢"), Map.entry("large_blue_circle", "🔵"),
        Map.entry("gear", "⚙️"), Map.entry("lock", "🔒"), Map.entry("bookmark", "🔖"), Map.entry("books", "📚"), Map.entry("email", "📧"),
        Map.entry("phone", "📞"), Map.entry("hammer_and_wrench", "🛠️"), Map.entry("green_heart", "💚"), Map.entry("raised_hand", "✋"));

    static final Pattern CODE = Pattern.compile(":([a-z0-9_+\\-]+):");

    private MsgParts() { }

    public static String emoji(String name) { return EMOJI.getOrDefault(name, ":" + name + ":"); }

    /// :name: codes inside text, as glyphs where known.
    public static String emojify(String text) {
        var m = CODE.matcher(text);
        var sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(emoji(m.group(1))));
        m.appendTail(sb);
        return sb.toString();
    }

    /// Each attachment as one piece of mrkdwn: its lead-in, its title (a link when it has one), its text; else its fallback.
    public static List<String> attachments(JsonNode m) {
        var out = new ArrayList<String>();
        m.path("attachments").forEach(a -> {
            var parts = new ArrayList<String>();
            if (!a.path("pretext").asText("").isBlank()) parts.add(a.path("pretext").asText());
            var title = a.path("title").asText("");
            var link = a.path("title_link").asText("");
            if (!title.isBlank()) parts.add(link.isBlank() ? "*" + title + "*" : "<" + link + "|" + title + ">");
            if (!a.path("text").asText("").isBlank()) parts.add(a.path("text").asText());
            if (parts.isEmpty() && !a.path("fallback").asText("").isBlank()) parts.add(a.path("fallback").asText());
            if (!parts.isEmpty()) out.add(String.join("\n", parts));
        });
        return out;
    }

    /// Files and the thread, one line each.
    public static String meta(JsonNode m) {
        var out = new ArrayList<String>();
        m.path("files").forEach(f -> out.add("📎  " + f.path("name").asText(f.path("title").asText("file"))));
        var n = m.path("reply_count").asInt();
        if (n > 0) out.add(n + (n == 1 ? " reply" : " replies"));
        return String.join("\n", out);
    }

    /// Reactions as "glyph count" pills.
    public static List<String> reactions(JsonNode m) {
        var out = new ArrayList<String>();
        m.path("reactions").forEach(r -> out.add(emoji(r.path("name").asText()) + "  " + r.path("count").asInt()));
        return out;
    }

    /// The whole message as a person reads it, for copying.
    public static String plain(JsonNode m, Map<String, String> labels) {
        var out = new ArrayList<String>();
        out.add(emojify(Mrkdwn.plain(m.path("text").asText(""), labels)));
        attachments(m).forEach(a -> out.add(Mrkdwn.plain(a, labels)));
        var x = meta(m);
        if (!x.isEmpty()) out.add(x);
        return String.join("\n", out).strip();
    }
}
