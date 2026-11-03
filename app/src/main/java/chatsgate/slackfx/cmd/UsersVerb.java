package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import module java.base;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "users", description = "People in the workspace, filtered by name, handle or email; or one person's full profile with --profile")
public final class UsersVerb implements Runnable {

    @Mixin Common c;
    @Parameters(arity = "0..1", description = "Fragment of name, handle or email; with --profile, a person (@name or U-id)") String filter;
    @Option(names = "--profile", description = "Show the full profile of one person") boolean profile;
    @Option(names = {"-n", "--limit"}, defaultValue = "50", description = "Most rows to print") int limit;

    @Override public void run() {
        var cx = c.open();
        if (profile) { one(cx); return; }
        var rows = cx.api.pages("users.list", new LinkedHashMap<>(Map.of("limit", 1000)), "members", 40).stream()
            .filter(u -> !u.path("deleted").asBoolean())
            .filter(u -> filter == null || haystack(u).contains(filter.toLowerCase())).limit(limit).toList();
        if (c.json) { rows.forEach(Out::json); return; }
        rows.forEach(u -> line(u.path("id").asText() + "  @" + u.path("name").asText() + "  " + Cx.display(u)
            + "  " + u.path("profile").path("email").asText("") + (u.path("is_bot").asBoolean() ? "  bot" : "")
            + "  " + u.path("profile").path("title").asText("")));
    }

    void one(Cx cx) {
        var r = cx.api.call("users.info", "user", cx.userId(filter));
        if (c.json) { Out.json(r); return; }
        var u = r.path("user");
        var p = u.path("profile");
        line("id        " + u.path("id").asText());
        line("handle    @" + u.path("name").asText());
        line("name      " + p.path("real_name").asText() + (p.path("display_name").asText("").isBlank() ? "" : " (" + p.path("display_name").asText() + ")"));
        line("title     " + p.path("title").asText(""));
        line("email     " + p.path("email").asText(""));
        line("phone     " + p.path("phone").asText(""));
        line("timezone  " + u.path("tz").asText("") + "  " + u.path("tz_label").asText(""));
        line("status    " + p.path("status_emoji").asText("") + " " + p.path("status_text").asText(""));
    }

    static String haystack(com.fasterxml.jackson.databind.JsonNode u) {
        return (u.path("name").asText() + " " + u.path("real_name").asText() + " " + Cx.display(u) + " "
            + u.path("profile").path("email").asText("")).toLowerCase();
    }
}
