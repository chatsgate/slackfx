package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;

/// Saves the files attached to messages (and to their replies) into a folder.
public final class Attach {

    private Attach() { }

    /// Returns file id -> saved name; files already on disk are kept and not fetched again.
    public static Map<String, String> save(Cx cx, List<JsonNode> msgs, Path dir) {
        var saved = new LinkedHashMap<String, String>();
        msgs.forEach(m -> {
            files(m).forEach(f -> one(cx, m.path("ts").asText(), f, dir, saved));
            m.path("replies_full").forEach(r -> files(r).forEach(f -> one(cx, r.path("ts").asText(), f, dir, saved)));
        });
        return saved;
    }

    static List<JsonNode> files(JsonNode m) {
        var out = new ArrayList<JsonNode>();
        m.path("files").forEach(f -> { if (!"tombstone".equals(f.path("mode").asText())) out.add(f); });
        return out;
    }

    static void one(Cx cx, String ts, JsonNode f, Path dir, Map<String, String> saved) {
        var id = f.path("id").asText();
        try {
            var url = f.path("url_private_download").asText(f.path("url_private").asText(""));
            if (url.isBlank()) url = cx.api.call("files.info", "file", id).path("file").path("url_private_download").asText("");
            if (url.isBlank()) { Out.err("  no download for file " + id); return; }
            var name = ts + "_" + id + "_" + f.path("name").asText(f.path("title").asText("file")).replaceAll("[\\\\/:*?\"<>|]", "_");
            var target = dir.resolve(name);
            if (!Files.exists(target)) {
                var res = cx.api.download(url);
                if (res.statusCode() != 200) { Out.err("  file " + id + " answered HTTP " + res.statusCode()); return; }
                Files.createDirectories(dir);
                Files.write(target, res.body());
            }
            saved.put(id, name);
        } catch (java.io.IOException | SlException e) {
            Out.err("  file " + id + " skipped: " + e.getMessage());
        }
    }
}
