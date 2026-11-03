package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import module java.base;

/// Whole-channel reads: every page of history, optionally with each thread's replies attached as `replies_full`.
public final class Fetch {

    private Fetch() { }

    /// Oldest first. `limit` 0 means every message in the range.
    public static List<JsonNode> messages(Cx cx, String channelId, String oldest, String latest, int limit, boolean replies) {
        var p = new LinkedHashMap<String, Object>(Map.of("channel", channelId, "limit", limit > 0 ? Math.min(limit, 200) : 200));
        if (oldest != null) p.put("oldest", oldest);
        if (latest != null) p.put("latest", latest);
        var got = new ArrayList<JsonNode>();
        for (var page = 0; page < 100_000 && (limit == 0 || got.size() < limit); page++) {
            var r = cx.api.call("conversations.history", p);
            r.path("messages").forEach(got::add);
            if (page % 5 == 4) Out.err("  fetched " + got.size() + " messages...");
            var next = r.path("response_metadata").path("next_cursor").asText("");
            if (next.isBlank() || !r.path("has_more").asBoolean(true)) break;
            p.put("cursor", next);
        }
        var out = new ArrayList<>(limit > 0 && got.size() > limit ? got.subList(0, limit) : got);
        Collections.reverse(out);
        if (replies) attachReplies(cx, channelId, out);
        return out;
    }

    static void attachReplies(Cx cx, String channelId, List<JsonNode> msgs) {
        var threads = msgs.stream().filter(m -> m.path("reply_count").asInt() > 0).toList();
        var n = 0;
        for (var m : threads) {
            var all = cx.api.pages("conversations.replies",
                new LinkedHashMap<>(Map.of("channel", channelId, "ts", m.path("ts").asText(), "limit", 200)), "messages", 1000);
            var arr = ((ObjectNode) m).putArray("replies_full");
            all.stream().skip(1).forEach(arr::add);
            if (++n % 10 == 0) Out.err("  read " + n + " of " + threads.size() + " threads...");
        }
    }
}
