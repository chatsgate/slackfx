package chatsgate.slackfx;

import javafx.scene.image.Image;
import chatsgate.slackfx.core.Cx;
import chatsgate.slackfx.core.Out;
import chatsgate.slackfx.core.Store;

import module java.base;

/// Every person's picture address, learnt from one users.list call (paged) rather than one call per person, and kept on
/// disk so the next start draws faces at once. Pictures load in JavaFX's own background loader and are kept per address.
public final class Avatars {

    final Map<String, String> urls = new ConcurrentHashMap<>();
    final Map<String, Image> images = new ConcurrentHashMap<>();

    static Path file(Cx cx) { return Store.home().resolve("cache").resolve(cx.session.teamId() + "-avatars.json"); }

    /// The addresses saved last time. Background lane.
    public void loadSaved(Cx cx) {
        try {
            if (Files.exists(file(cx))) Out.M.readTree(file(cx).toFile()).properties().forEach(e -> urls.put(e.getKey(), e.getValue().asText()));
        } catch (IOException e) {
            Log.warn("cannot read the saved picture addresses: " + e);
        }
    }

    /// The current addresses from Slack, saved for next time. Background lane.
    public void load(Cx cx) {
        var t0 = System.nanoTime();
        var fresh = new LinkedHashMap<String, String>();
        for (var u : cx.api.pages("users.list", new LinkedHashMap<String, Object>(Map.of("limit", 1000)), "members", 20)) {
            var url = u.path("profile").path("image_72").asText("");
            if (!url.isEmpty()) fresh.put(u.path("id").asText(), url);
        }
        urls.putAll(fresh);
        Log.info("pictures: " + fresh.size() + " addresses in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        try {
            Files.createDirectories(file(cx).getParent());
            Out.M.writeValue(file(cx).toFile(), fresh);
        } catch (IOException e) {
            Log.warn("cannot save the picture addresses: " + e);
        }
    }

    /// A person's picture, or null when the address is not known (yet). FX thread.
    public Image of(String userId) {
        var url = userId == null ? null : urls.get(userId);
        return url == null ? null : at(url);
    }

    /// A message's sender picture: a person's from the address book, an app's from the icon its message carries. FX thread.
    public Image face(com.fasterxml.jackson.databind.JsonNode m) {
        var u = m.path("user").asText("");
        var icons = m.path("bot_profile").path("icons");
        if (icons.isMissingNode()) icons = m.path("icons");
        var bot = icons.path("image_72").asText(icons.path("image_48").asText(icons.path("image_36").asText("")));
        return !m.has("bot_id") && !u.isEmpty() ? of(u) : !bot.isEmpty() ? at(bot) : of(u);
    }

    /// The picture at an address (a bot's icon comes with its message). FX thread.
    public Image at(String url) {
        return url == null || url.isEmpty() ? null : images.computeIfAbsent(url, u -> new Image(u, 72, 72, true, true, true));
    }
}
