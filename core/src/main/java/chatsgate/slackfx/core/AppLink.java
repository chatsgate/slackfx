package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;

import static chatsgate.slackfx.core.Out.M;

/// ~/chatsgate/slackfx/run/app.json: where slackfx listens and the token a caller must present.
/// Loopback only, in the user's own profile folder. The CLI reads this to route a gated write to the app.
public final class AppLink {

    private AppLink() { }

    public static Path file() { return Store.home().resolve("run").resolve("app.json"); }

    public static void write(int port, String token) {
        try {
            Files.createDirectories(file().getParent());
            M.writeValue(file().toFile(), M.createObjectNode().put("pid", ProcessHandle.current().pid())
                .put("port", port).put("token", token).put("startedAt", Instant.now().toString()));
        } catch (IOException e) {
            throw new SlException("cannot write " + file(), e);
        }
    }

    public static JsonNode read() {
        try {
            return Files.exists(file()) ? M.readTree(file().toFile()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /// The app.json of a live app, or null if none is running (stale file is ignored).
    public static JsonNode live() {
        var info = read();
        if (info == null) return null;
        return ProcessHandle.of(info.path("pid").asLong()).filter(ProcessHandle::isAlive).isPresent() ? info : null;
    }

    public static void delete() { file().toFile().delete(); }
}
