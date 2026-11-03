package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import module java.base;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "send", description = {"Post a message, a thread reply (--thread), or a scheduled message (--at).",
    "Dry run by default: it prints exactly what would be posted. Add --yes to post it, as you."})
public final class SendVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Channel: id, #name, @person, or a message permalink (then it replies in that thread)") String channel;
    @Parameters(index = "1", arity = "0..1", description = "Message text (or use --file)") String text;
    @Option(names = "--file", description = "Read the message text from this file") Path file;
    @Option(names = "--thread", description = "Reply in the thread of this message ts or permalink") String thread;
    @Option(names = "--broadcast", description = "With --thread, also show the reply in the channel") boolean broadcast;
    @Option(names = "--at", description = "Schedule: 2026-10-17T09:00Z or epoch seconds") String at;
    @Option(names = "--yes", description = "Really post it") boolean yes;

    @Override public void run() throws RuntimeException {
        var body = body();
        var cx = c.open();
        var id = cx.channelId(channel);
        var th = thread != null ? Ref.ts(thread) : Ref.threadTs(channel);
        line((yes ? "posting" : "DRY RUN, would post") + " to " + cx.channelLabel(id) + " as " + cx.session.userName()
            + (th != null ? " in thread " + th : "") + (at != null ? " at " + at : "") + ":");
        line(body);
        if (!yes) { line("-- nothing sent; add --yes"); return; }
        if (at != null) {
            var d = Drafts.schedule(cx, id, th, body, Ref.parse(at));
            if (c.json) Out.json(d); else line("scheduled  " + d.path("id").asText() + "  for " + Ref.show(d.path("date_scheduled").asText()));
            return;
        }
        var p = new LinkedHashMap<String, Object>(Map.of("channel", id, "text", body));
        if (th != null) p.put("thread_ts", th);
        if (th != null && broadcast) p.put("reply_broadcast", true);
        var r = cx.write("chat.postMessage", p);
        if (c.json) { Out.json(r); return; }
        var ts = r.path("ts").asText();
        line("sent  " + ts + "  " + cx.api.call("chat.getPermalink", "channel", id, "message_ts", ts).path("permalink").asText());
    }

    String body() {
        try {
            var b = file != null ? Files.readString(file) : text;
            if (b == null || b.isBlank()) throw new SlException("no message text -- give it as an argument or with --file");
            return b.strip();
        } catch (java.io.IOException e) {
            throw new SlException("cannot read " + file, e);
        }
    }
}
