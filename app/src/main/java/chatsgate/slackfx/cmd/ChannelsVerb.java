package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import module java.base;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "channels", description = "Channels you are in (or every public one with --all), optionally filtered by name")
public final class ChannelsVerb implements Runnable {

    @Mixin Common c;
    @Parameters(arity = "0..1", description = "Name fragment to filter by") String filter;
    @Option(names = "--all", description = "Every public channel in the workspace, not only yours") boolean all;
    @Option(names = "--types", defaultValue = "public_channel,private_channel",
        description = "Comma list of public_channel,private_channel,mpim,im (default: ${DEFAULT-VALUE})") String types;
    @Option(names = {"-n", "--limit"}, defaultValue = "100", description = "Most rows to print") int limit;

    @Override public void run() {
        var cx = c.open();
        var p = new LinkedHashMap<String, Object>(Map.of("exclude_archived", true, "limit", 1000, "types", types));
        var rows = cx.api.pages(all ? "conversations.list" : "users.conversations", p, "channels", 40).stream()
            .filter(ch -> filter == null || name(cx, ch).toLowerCase().contains(filter.toLowerCase())).limit(limit).toList();
        if (c.json) { rows.forEach(Out::json); return; }
        rows.forEach(ch -> line(ch.path("id").asText() + "  " + name(cx, ch)
            + (ch.has("num_members") ? "  " + ch.path("num_members").asInt() + " members" : "")
            + (ch.path("is_private").asBoolean() ? "  private" : "")
            + topic(ch)));
    }

    static String name(Cx cx, com.fasterxml.jackson.databind.JsonNode ch) {
        return ch.path("is_im").asBoolean() ? "@" + cx.userName(ch.path("user").asText()) : "#" + ch.path("name").asText(ch.path("id").asText());
    }

    static String topic(com.fasterxml.jackson.databind.JsonNode ch) {
        var t = ch.path("topic").path("value").asText("");
        return t.isBlank() ? "" : "  — " + Out.oneLine(t, 80);
    }
}
