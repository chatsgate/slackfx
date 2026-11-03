package chatsgate.slackfx;

import javafx.scene.Cursor;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.awt.Desktop;

import module java.base;

/// Slack's own markup ("mrkdwn") drawn into a TextFlow: *bold*, _italic_, ~strike~, `code`, ```blocks```, > quotes, and
/// the <...> tokens Slack sends for people, channels, links and @here. Names come from a map built on the background
/// lane, so drawing never touches the network. Anything it does not recognise is shown as typed.
public final class Mrkdwn {

    static final String BASE = "-fx-font-size: 15; -fx-fill: -slk-text;", BOLD = "-fx-font-weight: bold;", ITALIC = "-fx-font-style: italic;",
        CODE = "-fx-font-family: 'Consolas'; -fx-font-size: 12; -fx-fill: -slk-code;", LINK = "-fx-fill: -slk-link;",
        MENTION = "-fx-fill: -slk-mention; -fx-font-weight: bold;", QUOTE = "-fx-fill: -slk-muted;";

    /// Emphasis may wrap a <...> token (a link whose address has underscores in it, say), so a token counts as one unit inside.
    static final Pattern INLINE = Pattern.compile("<([^<>\\n]+)>|`([^`\\n]+)`"
        + "|(?<![\\w*])\\*(?![\\s*])((?:<[^<>\\n]+>|[^*\\n])+?)(?<!\\s)\\*(?![\\w*])"
        + "|(?<![\\w_])_(?![\\s_])((?:<[^<>\\n]+>|[^_\\n])+?)(?<!\\s)_(?![\\w_])"
        + "|(?<![\\w~])~(?![\\s~])((?:<[^<>\\n]+>|[^~\\n])+?)(?<!\\s)~(?![\\w~])");
    static final Pattern TOKEN = Pattern.compile("<([^<>\\n]+)>"), QUOTED = Pattern.compile("^\\s*(?:>|&gt;)\\s?(.*)$");

    private Mrkdwn() { }

    public static void fill(TextFlow flow, String text, Map<String, String> labels) {
        flow.getChildren().clear();
        var fenced = false;
        var lines = (text == null ? "" : text).split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            if (i > 0) add(flow, "\n", BASE);
            var parts = lines[i].split("```", -1);
            for (var j = 0; j < parts.length; j++) {
                if (j > 0) fenced = !fenced;
                var p = parts[j];
                if (p.isEmpty()) continue;
                if (fenced) add(flow, unescape(p), BASE + CODE);
                else line(flow, p, labels, j == 0);
            }
        }
    }

    static void line(TextFlow flow, String s, Map<String, String> labels, boolean lineStart) {
        var m = QUOTED.matcher(s);
        if (lineStart && m.matches()) { add(flow, "▎ ", BASE + QUOTE); inline(flow, m.group(1), labels, BASE + QUOTE); }
        else inline(flow, s, labels, BASE);
    }

    static void inline(TextFlow flow, String s, Map<String, String> labels, String base) {
        var m = INLINE.matcher(s);
        var at = 0;
        while (m.find()) {
            if (m.start() > at) add(flow, unescape(s.substring(at, m.start())), base);
            if (m.group(1) != null) token(flow, m.group(1), labels, base);
            else if (m.group(2) != null) add(flow, unescape(m.group(2)), base + CODE);
            else if (m.group(3) != null) inline(flow, m.group(3), labels, base + BOLD);
            else if (m.group(4) != null) inline(flow, m.group(4), labels, base + ITALIC);
            else inline(flow, m.group(5), labels, base + "-fx-strikethrough: true;");
            at = m.end();
        }
        if (at < s.length()) add(flow, unescape(s.substring(at)), base);
    }

    static void token(TextFlow flow, String t, Map<String, String> labels, String base) {
        var c = t.charAt(0);
        if (c == '@' || c == '#' || c == '!') add(flow, tokenText(t, labels), base + MENTION);
        else flow.getChildren().add(link(tokenText(t, labels), t.split("\\|", 2)[0], base));
    }

    /// What a <...> token reads as: @person, #channel, @here, a date's own fallback text, or a link's label.
    static String tokenText(String t, Map<String, String> labels) {
        var bar = t.indexOf('|');
        var head = bar < 0 ? t : t.substring(0, bar);
        var tail = bar < 0 ? null : t.substring(bar + 1);
        return switch (head.isEmpty() ? ' ' : head.charAt(0)) {
            case '@' -> "@" + (tail != null ? tail : labels.getOrDefault(head.substring(1), head.substring(1)));
            case '#' -> tail != null ? "#" + tail : labels.getOrDefault(head.substring(1), head);
            case '!' -> special(head.substring(1), tail);
            default -> unescape(tail != null ? tail : head.replaceFirst("^mailto:", ""));
        };
    }

    static String special(String name, String tail) {
        if (tail != null) return tail.startsWith("@") || name.startsWith("date") ? tail : "@" + tail;
        return "@" + name.replaceFirst("\\^.*", "");
    }

    /// The text as a person reads it, without markup: for copying a message and for one-line previews.
    public static String plain(String text, Map<String, String> labels) {
        var m = TOKEN.matcher(text == null ? "" : text);
        var sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(tokenText(m.group(1), labels)));
        m.appendTail(sb);
        return unescape(sb.toString());
    }

    static String unescape(String s) { return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"); }

    static void add(TextFlow flow, String s, String style) {
        var t = new Text(s);
        t.setStyle(style);
        flow.getChildren().add(t);
    }

    static Text link(String label, String url, String base) {
        var t = new Text(label);
        t.setStyle(base + LINK);
        t.setCursor(Cursor.HAND);
        t.setOnMouseClicked(e -> Thread.ofVirtual().start(() -> {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ex) {
                Log.warn("cannot open " + url + ": " + ex);
            }
        }));
        return t;
    }
}
