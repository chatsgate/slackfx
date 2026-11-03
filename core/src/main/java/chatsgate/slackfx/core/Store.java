package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import module java.base;

public final class Store {

    static final ObjectMapper JSON = new ObjectMapper();

    private Store() { }

    /// The app's home on this machine: `-Dslackfx.home`, else `~/chatsgate/slackfx`. An install made before the project moved keeps
    /// its tokens and cache in `~/fxslack/slackfx`; that folder is used as long as the new one does not exist.
    public static Path home() {
        var set = System.getProperty("slackfx.home");
        if (set != null) return Path.of(set);
        var user = Path.of(System.getProperty("user.home"));
        var now = user.resolve("chatsgate").resolve("slackfx");
        var before = user.resolve("fxslack").resolve("slackfx");
        return !Files.exists(now) && Files.exists(before) ? before : now;
    }

    public static Path sessionsPath() { return home().resolve("sessions.json"); }

    public static Path cachePath(String teamId) { return home().resolve("cache").resolve(teamId + ".json"); }

    /// The OAuth app's config, written once at install: holds the durable `user_token` (xoxp-) and app credentials.
    public static Path appConfigPath() { return home().resolve("app-config.json"); }

    /// Writes need a person's approval unless app-config.json says "require_approval": false. Also true when there is no such file.
    public static boolean requireApproval() {
        return !Files.exists(appConfigPath()) || appConfig().path("require_approval").asBoolean(true);
    }

    public static JsonNode appConfig() {
        var p = appConfigPath();
        if (!Files.exists(p)) throw new SlException("no " + p + " -- the app has not been set up yet");
        try {
            return JSON.readTree(p.toFile());
        } catch (Exception e) {
            throw new SlException("cannot read " + p, e);
        }
    }

    public static SlSessions loadSessions() { return read(sessionsPath(), SlSessions.class, SlSessions::new); }

    public static void saveSessions(SlSessions s) { write(sessionsPath(), s); }

    public static Cache loadCache(String teamId) { return read(cachePath(teamId), Cache.class, Cache::new); }

    public static void saveCache(String teamId, Cache c) { write(cachePath(teamId), c); }

    static <T> T read(Path p, Class<T> type, Supplier<T> empty) {
        if (!Files.exists(p)) return empty.get();
        try {
            return JSON.readValue(p.toFile(), type);
        } catch (Exception e) {
            throw new SlException("cannot read " + p, e);
        }
    }

    static void write(Path p, Object v) {
        try {
            Files.createDirectories(p.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), v);
        } catch (Exception e) {
            throw new SlException("cannot write " + p, e);
        }
    }
}
