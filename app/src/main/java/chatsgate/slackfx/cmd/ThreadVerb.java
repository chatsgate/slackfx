package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import module java.base;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "thread", description = {"Read a whole thread: the parent message and every reply.",
    "Give a message permalink alone, or a channel and the parent's ts."})
public final class ThreadVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Message permalink, or the channel") String channel;
    @Parameters(index = "1", arity = "0..1", description = "Parent message ts (not needed with a permalink)") String ts;

    @Override public void run() {
        var cx = c.open();
        var id = cx.channelId(channel);
        var parent = ts != null ? Ref.ts(ts) : Optional.ofNullable(Ref.threadTs(channel)).orElse(Ref.ts(channel));
        var msgs = cx.api.pages("conversations.replies", new LinkedHashMap<>(Map.of("channel", id, "ts", parent, "limit", 200)), "messages", 20);
        if (c.json) { msgs.forEach(Out::json); return; }
        line("# thread in " + cx.channelLabel(id) + "  " + msgs.size() + " messages");
        msgs.forEach(m -> line(Render.message(cx, m)));
    }
}
