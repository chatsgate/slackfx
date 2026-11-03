package chatsgate.slackfx;

import javafx.application.Platform;
import chatsgate.slackfx.core.Cx;
import chatsgate.slackfx.core.Out;
import chatsgate.slackfx.core.Store;

import module java.base;

/// Which conversations hold messages you have not seen, and how many. On start one sweep asks Slack (conversations.info:
/// latest message, last_read, unread count) at about one call a second on its own thread, so it never holds up the lane
/// that loads and sends; after that, live events keep it current. What you open here is remembered locally as "seen up
/// to" (marking read on Slack's side needs write scopes the app does not have), so a sweep never re-lights it.
public final class Unread {

    final Map<String, Integer> counts = new HashMap<>();
    final Map<String, String> seen = new ConcurrentHashMap<>();
    final Path file;
    final Path countsFile;
    final Runnable changed;

    public Unread(Cx cx, Runnable changed) {
        this.file = Store.home().resolve("cache").resolve(cx.session.teamId() + "-seen.json");
        this.countsFile = Store.home().resolve("cache").resolve(cx.session.teamId() + "-unread.json");
        this.changed = changed;
        try {
            if (Files.exists(file)) Out.M.readTree(file.toFile()).properties().forEach(e -> seen.put(e.getKey(), e.getValue().asText()));
            // what was lit when the app last ran is lit again at once; the sweep then confirms or clears it
            if (Files.exists(countsFile)) Out.M.readTree(countsFile.toFile()).properties().forEach(e -> counts.put(e.getKey(), e.getValue().asInt(1)));
        } catch (IOException e) {
            Log.warn("cannot read the seen marks: " + e);
        }
    }

    public boolean has(String conv) { return counts.getOrDefault(conv, 0) > 0; }

    public int count(String conv) { return counts.getOrDefault(conv, 0); }

    /// One more unseen message (a live event). FX thread.
    public void bump(String conv) { counts.merge(conv, 1, Integer::sum); saveCounts(); changed.run(); }

    /// The conversation was opened and read up to `ts`. FX thread.
    public void clear(String conv, String ts) {
        var had = counts.remove(conv) != null;
        if (ts != null && !ts.isEmpty() && ts.compareTo(seen.getOrDefault(conv, "")) > 0) { seen.put(conv, ts); save(); }
        if (had) { saveCounts(); changed.run(); }
    }

    /// Asks Slack once per conversation, gently, and lights those with messages newer than both Slack's read mark and ours.
    public void sweep(Cx cx, List<String> convs) {
        Thread.ofVirtual().name("unread-sweep").start(() -> {
            var t0 = System.nanoTime();
            var lit = 0;
            for (var id : convs) {
                try {
                    var c = cx.api.call("conversations.info", "channel", id).path("channel");
                    var latest = c.path("latest").path("ts").asText("");
                    // for a channel Slack gives only last_read (no latest message, no counts): ask for the newest message
                    if (latest.isEmpty()) latest = cx.api.call("conversations.history", "channel", id, "limit", 1).path("messages").path(0).path("ts").asText("");
                    var read = c.path("last_read").asText("");
                    var mine = seen.getOrDefault(id, "");
                    var n = Math.max(c.path("unread_count_display").asInt(0), 1);
                    if (!latest.isEmpty() && latest.compareTo(read) > 0 && latest.compareTo(mine) > 0) {
                        lit++;
                        Platform.runLater(() -> { counts.put(id, n); saveCounts(); changed.run(); });
                    } else Platform.runLater(() -> { if (counts.remove(id) != null) { saveCounts(); changed.run(); } }); // read elsewhere since
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                } catch (RuntimeException e) {
                    Log.warn("unread sweep, " + id + ": " + e.getMessage());
                }
            }
            Log.info("unread sweep: " + lit + " of " + convs.size() + " conversations have unseen messages (" + (System.nanoTime() - t0) / 1_000_000 + " ms)");
        });
    }

    /// The lit conversations, for the next start. FX thread (the only one that touches `counts`).
    void saveCounts() {
        try {
            Files.createDirectories(countsFile.getParent());
            Out.M.writeValue(countsFile.toFile(), new TreeMap<>(counts));
        } catch (IOException e) {
            Log.warn("cannot save the unread counts: " + e);
        }
    }

    void save() {
        try {
            Files.createDirectories(file.getParent());
            Out.M.writeValue(file.toFile(), new TreeMap<>(seen));
        } catch (IOException e) {
            Log.warn("cannot save the seen marks: " + e);
        }
    }
}
