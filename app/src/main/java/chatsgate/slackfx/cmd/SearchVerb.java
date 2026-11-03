package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "search", description = {"Search messages across the workspace (public and private, everything you can see).",
    "The search syntax of the workspace works: from:@name in:#channel before:2026-10-01 after:2026-09-01 has:link \"exact phrase\"."})
public final class SearchVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Query") String query;
    @Option(names = {"-n", "--limit"}, defaultValue = "20", description = "Results per page (max 100)") int limit;
    @Option(names = "--page", defaultValue = "1", description = "Page number") int page;
    @Option(names = "--oldest-first", description = "Sort by time ascending (default: newest first)") boolean ascending;
    @Option(names = "--best", description = "Sort by relevance instead of time") boolean best;

    @Override public void run() {
        var cx = c.open();
        var r = cx.api.call("search.messages", "query", query, "count", Math.min(limit, 100), "page", page,
            "sort", best ? "score" : "timestamp", "sort_dir", ascending ? "asc" : "desc");
        if (c.json) { Out.json(r); return; }
        var m = r.path("messages");
        var pg = m.path("paging");
        line("# " + m.path("total").asInt() + " matches, page " + pg.path("page").asInt(1) + " of " + pg.path("pages").asInt(1));
        m.path("matches").forEach(x -> {
            var ch = x.path("channel");
            line(Ref.show(x.path("ts").asText()) + "  " + (ch.path("is_im").asBoolean() ? "DM" : "#" + ch.path("name").asText("?"))
                + "  @" + x.path("username").asText(cx.userName(x.path("user").asText())) + ": "
                + Out.oneLine(Render.clean(cx, Render.text(x)), 300));
            line("    " + x.path("permalink").asText());
        });
    }
}
