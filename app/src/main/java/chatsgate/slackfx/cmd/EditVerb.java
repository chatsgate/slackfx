package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "edit", description = "Edit one of your own messages. Dry run unless --yes.")
public final class EditVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Message permalink, or the channel") String channel;
    @Parameters(index = "1", description = "Message ts (omit when a permalink was given): then the new text follows") String a;
    @Parameters(index = "2", arity = "0..1", description = "New text") String b;
    @Option(names = "--yes", description = "Really change it") boolean yes;

    @Override public void run() {
        var cx = c.open();
        var id = cx.channelId(channel);
        var link = Ref.PERMALINK.matcher(channel).find();
        var ts = link ? Ref.ts(channel) : Ref.ts(a);
        var text = link ? a : b;
        if (text == null) throw new SlException("no new text given");
        line((yes ? "editing " : "DRY RUN, would edit ") + ts + " in " + cx.channelLabel(id) + " to:");
        line(text);
        if (!yes) { line("-- nothing changed; add --yes"); return; }
        var r = cx.write("chat.update", "channel", id, "ts", ts, "text", text);
        if (c.json) Out.json(r); else line("edited  " + ts);
    }

    @Command(name = "delete", description = "Delete one of your own messages. Dry run unless --yes.")
    public static final class Delete implements Runnable {
        @Mixin Common c;
        @Parameters(index = "0", description = "Message permalink, or the channel") String channel;
        @Parameters(index = "1", arity = "0..1", description = "Message ts (omit when a permalink was given)") String ts;
        @Option(names = "--yes", description = "Really delete it") boolean yes;

        @Override public void run() {
            var cx = c.open();
            var id = cx.channelId(channel);
            var t = Ref.ts(ts != null ? ts : channel);
            line((yes ? "deleting " : "DRY RUN, would delete ") + t + " in " + cx.channelLabel(id));
            if (!yes) { line("-- nothing deleted; add --yes"); return; }
            var r = cx.write("chat.delete", "channel", id, "ts", t);
            if (c.json) Out.json(r); else line("deleted  " + t);
        }
    }
}
