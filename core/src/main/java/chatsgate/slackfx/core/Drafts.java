package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;

import static chatsgate.slackfx.core.Out.M;

/// Scheduled messages as the web app makes them: a draft with date_scheduled. chat.scheduleMessage refuses browser tokens.
public final class Drafts {

    private Drafts() { }

    public static JsonNode schedule(Cx cx, String channelId, String threadTs, String text, String postAt) {
        var block = M.createObjectNode().put("type", "rich_text");
        block.putArray("elements").addObject().put("type", "rich_text_section")
            .putArray("elements").addObject().put("type", "text").put("text", text);
        var dest = M.createObjectNode().put("channel_id", channelId);
        if (threadTs != null) dest.put("thread_ts", threadTs);
        try {
            return cx.write("drafts.create", "client_msg_id", UUID.randomUUID().toString(),
                "blocks", M.createArrayNode().add(block).toString(), "destinations", M.createArrayNode().add(dest).toString(),
                "date_scheduled", postAt, "file_ids", "[]", "is_from_composer", false).path("draft");
        } catch (SlException e) {
            throw new SlException(e.getMessage() + " -- scheduling through a browser session is not working yet"
                + " (chat.scheduleMessage refuses it; the web app's drafts call still needs one field captured). Nothing was scheduled.");
        }
    }

    public static List<JsonNode> scheduled(Cx cx) {
        var out = new ArrayList<JsonNode>();
        cx.api.call("drafts.list").path("drafts").forEach(d -> {
            if (d.path("date_scheduled").asLong() > 0 && !d.path("is_sent").asBoolean() && !d.path("is_deleted").asBoolean()) out.add(d);
        });
        return out;
    }

    public static void cancel(Cx cx, JsonNode draft) {
        cx.write("drafts.delete", "draft_id", draft.path("id").asText(), "client_last_updated_ts", draft.path("last_updated_ts").asText());
    }

    /// The plain text inside a draft's rich_text blocks.
    public static String text(JsonNode draft) {
        var sb = new StringBuilder();
        draft.path("blocks").findValues("text").forEach(t -> { if (t.isTextual()) sb.append(t.asText()); });
        return sb.toString();
    }
}
