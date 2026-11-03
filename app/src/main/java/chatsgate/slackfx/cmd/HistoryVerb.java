package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "history", aliases = "read", description = {"Read a channel's messages, oldest first.",
    "The channel is an id, #name, @person (a DM) or a message permalink. To save a whole channel with files use 'export'."})
public final class HistoryVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Channel") String channel;
    @Option(names = {"-n", "--limit"}, defaultValue = "30", description = "Most messages to fetch (default ${DEFAULT-VALUE})") int limit;
    @Option(names = "--all", description = "Every message in the range, however many") boolean all;
    @Option(names = "--replies", description = "Read each thread's replies under its parent") boolean replies;
    @Option(names = "--since", description = "Only after this time: 2026-10-01, 2026-10-01T09:30Z or epoch seconds") String since;
    @Option(names = "--until", description = "Only before this time") String until;

    @Override public void run() {
        var cx = c.open();
        var id = cx.channelId(channel);
        var msgs = Fetch.messages(cx, id, Ref.parse(since), Ref.parse(until), all ? 0 : limit, replies);
        if (c.json) { msgs.forEach(Out::json); return; }
        line("# " + cx.channelLabel(id) + " (" + id + ")  " + msgs.size() + " messages");
        msgs.forEach(m -> line(Render.withReplies(cx, m)));
    }
}
