package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.node.ObjectNode;

import module java.base;

/// The newest messages of each conversation as last seen, one small file each, so opening a conversation shows them at once and Slack
/// refreshes them behind. It is only a cache: a file that cannot be read is ignored and written again after the next fetch.
public final class MsgCache {

    public static final int KEEP = 200;

    private MsgCache() { }

    static Path file(String team, String conv) { return Store.home().resolve("cache").resolve(team + "-messages").resolve(conv + ".json"); }

    /// Null when nothing usable is saved for this conversation.
    public static SavedMessages load(String team, String conv) {
        var f = file(team, conv);
        if (!Files.exists(f)) return null;
        try {
            var t = Store.JSON.readTree(f.toFile());
            var messages = new ArrayList<ObjectNode>();
            t.path("messages").forEach(m -> { if (m instanceof ObjectNode o) messages.add(o); });
            var labels = new HashMap<String, String>();
            t.path("labels").properties().forEach(e -> labels.put(e.getKey(), e.getValue().asText()));
            return messages.isEmpty() ? null : new SavedMessages(messages, labels, t.path("more").asBoolean());
        } catch (Exception e) {
            return null;
        }
    }

    /// Saves the newest KEEP of `messages` (oldest first). `more` says Slack has older ones than the oldest given.
    public static void save(String team, String conv, List<ObjectNode> messages, Map<String, String> labels, boolean more) {
        var keep = messages.subList(Math.max(0, messages.size() - KEEP), messages.size());
        var t = Store.JSON.createObjectNode();
        t.put("more", more || keep.size() < messages.size());
        var l = t.putObject("labels");
        labels.forEach(l::put);
        var a = t.putArray("messages");
        keep.forEach(a::add);
        try {
            var f = file(team, conv);
            Files.createDirectories(f.getParent());
            var tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Store.JSON.writeValue(tmp.toFile(), t);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // a cache that cannot be written only costs the next open its speed
        }
    }

    /// The fresh newest page laid over what was saved: the saved messages older than the page, then the page. When the page does not
    /// reach back to the saved ones (more news than a page arrived), the saved ones would leave a gap and are dropped.
    public static List<ObjectNode> merge(List<ObjectNode> saved, List<ObjectNode> page) {
        if (saved == null || saved.isEmpty() || page.isEmpty()) return page;
        var first = page.getFirst().path("ts").asText();
        if (first.compareTo(saved.getLast().path("ts").asText()) > 0) return page;
        var out = new ArrayList<ObjectNode>(saved.stream().filter(m -> m.path("ts").asText().compareTo(first) < 0).toList());
        out.addAll(page);
        return out;
    }
}
