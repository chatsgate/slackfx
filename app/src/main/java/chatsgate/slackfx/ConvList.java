package chatsgate.slackfx;

import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import chatsgate.slackfx.core.Cx;
import chatsgate.slackfx.core.Out;
import chatsgate.slackfx.core.Store;

import module java.base;
import javafx.scene.control.*;

/// The sidebar list, in Slack's sections: "Channels" A-Z, then "Direct messages" most recently updated first. Rows are
/// Slack's own conversation objects with a "kind" and a "label" added; a section heading is a row with a "section" field
/// that cannot be selected. The last list is kept on disk and shown at once on the next start (Slack takes seconds).
public final class ConvList {

    final ObservableList<ObjectNode> rows = FXCollections.observableArrayList();
    final FilteredList<ObjectNode> shown = new FilteredList<>(rows);
    final ListView<ObjectNode> view = new ListView<>(shown);

    public ConvList(Avatars avatars, Unread unread) {
        view.getStyleClass().add("conv-list");
        view.setFocusTraversable(false);
        view.setCellFactory(v -> new ConvCell(avatars, unread));
        view.setMinWidth(290);
        view.setPrefWidth(290);
        view.setMaxWidth(290);
        var wait = new Label("loading conversations...");
        wait.getStyleClass().add("placeholder");
        view.setPlaceholder(wait);
    }

    static Path file(Cx cx) { return Store.home().resolve("cache").resolve(cx.session.teamId() + "-conversations.json"); }

    /// The list as it was last time; empty when there is none yet.
    public static List<ObjectNode> cached(Cx cx) {
        var out = new ArrayList<ObjectNode>();
        try {
            if (Files.exists(file(cx))) Out.M.readTree(file(cx).toFile()).forEach(n -> { if (n instanceof ObjectNode o) out.add(o); });
        } catch (IOException e) {
            Log.warn("cannot read the saved conversation list: " + e);
        }
        return out;
    }

    /// Lists the conversations and works out their names, then saves the list. Background lane only.
    public static List<ObjectNode> load(Cx cx) {
        var p = new LinkedHashMap<String, Object>(Map.of("types", "public_channel,private_channel,mpim,im", "exclude_archived", true, "limit", 1000));
        var out = new ArrayList<ObjectNode>();
        var t0 = System.nanoTime();
        var all = cx.api.pages("users.conversations", p, "channels", 20);
        Log.info("users.conversations answered " + all.size() + " in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        for (var c : all) {
            if (!(c instanceof ObjectNode o) || o.path("is_user_deleted").asBoolean()) continue;
            var kind = o.path("is_im").asBoolean() ? "im" : o.path("is_mpim").asBoolean() ? "mpim" : o.path("is_private").asBoolean() ? "private" : "public";
            o.put("kind", kind);
            o.put("label", switch (kind) {
                case "im" -> cx.userName(o.path("user").asText());
                case "mpim" -> group(o.path("name").asText());
                default -> o.path("name").asText();
            });
            out.add(o);
        }
        out.sort(Comparator.comparingInt((ObjectNode o) -> dm(o) ? 1 : 0)
            .thenComparingLong(o -> dm(o) ? -o.path("updated").asLong() : 0L)
            .thenComparing(o -> o.path("label").asText().toLowerCase()));
        try {
            Files.createDirectories(file(cx).getParent());
            Out.M.writeValue(file(cx).toFile(), out);
        } catch (IOException e) {
            Log.warn("cannot save the conversation list: " + e);
        }
        return out;
    }

    static boolean dm(ObjectNode o) { var k = o.path("kind").asText(); return k.equals("im") || k.equals("mpim"); }

    static boolean heading(ObjectNode o) { return o != null && o.has("section"); }

    /// "mpdm-alice--bob--carol-1" reads as "alice, bob, carol".
    static String group(String name) { return name.replaceFirst("^mpdm-", "").replaceFirst("-\\d+$", "").replace("--", ", "); }

    /// What the conversation is called in the message pane's title.
    public static String title(ObjectNode c) {
        return switch (c.path("kind").asText()) {
            case "im" -> c.path("label").asText();
            case "mpim" -> c.path("label").asText();
            default -> "# " + c.path("label").asText();
        };
    }

    public void show(List<ObjectNode> all) {
        var keep = selected();
        var withHeadings = new ArrayList<ObjectNode>();
        withHeadings.add(Out.M.createObjectNode().put("section", "Channels"));
        all.stream().filter(o -> !dm(o)).forEach(withHeadings::add);
        withHeadings.add(Out.M.createObjectNode().put("section", "Direct messages"));
        all.stream().filter(ConvList::dm).forEach(withHeadings::add);
        rows.setAll(withHeadings);
        if (keep != null) select(keep.path("id").asText());
        else move(1);
    }

    /// While a filter is typed the section headings step aside, as in Slack's quick switcher.
    public void filter(String text) {
        var f = text.toLowerCase().strip();
        shown.setPredicate(r -> f.isEmpty() ? true : !heading(r) && r.path("label").asText().toLowerCase().contains(f));
        if (selected() == null) move(1);
    }

    public ObjectNode selected() { var s = view.getSelectionModel().getSelectedItem(); return heading(s) ? null : s; }

    public boolean has(String id) { return rows.stream().anyMatch(r -> r.path("id").asText().equals(id)); }

    /// The ids of every conversation, for the unread sweep.
    public List<String> ids() { return rows.stream().filter(r -> !heading(r)).map(r -> r.path("id").asText()).toList(); }

    /// A direct message just had a message: up to the top of its section, as in Slack. Channels keep their A-Z place.
    public void touch(String id) {
        var row = rows.stream().filter(r -> r.path("id").asText().equals(id)).findFirst().orElse(null);
        if (row == null || !dm(row)) return;
        var keep = selected();
        rows.remove(row);
        var at = 0;
        while (at < rows.size() && !"Direct messages".equals(rows.get(at).path("section").asText())) at++;
        rows.add(Math.min(at + 1, rows.size()), row);
        if (keep != null) select(keep.path("id").asText());
    }

    void select(String id) {
        for (var i = 0; i < shown.size(); i++) if (shown.get(i).path("id").asText().equals(id)) { view.getSelectionModel().select(i); return; }
    }

    /// Moves the selection, stepping over section headings.
    public void move(int step) {
        var n = shown.size();
        var i = view.getSelectionModel().getSelectedIndex();
        do i += step; while (i >= 0 && i < n && heading(shown.get(i)));
        if (i < 0 || i >= n) return;
        view.getSelectionModel().select(i);
        view.scrollTo(Math.max(0, i - 3));
    }
}
