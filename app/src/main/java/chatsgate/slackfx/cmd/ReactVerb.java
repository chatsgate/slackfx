package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "react", description = "Add (or with --remove, take away) an emoji reaction on a message")
public final class ReactVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Message permalink, or the channel") String channel;
    @Parameters(index = "1", description = "Emoji name (e.g. thumbsup), or the message ts when the channel was given first") String a;
    @Parameters(index = "2", arity = "0..1", description = "Emoji name when a channel and ts were given") String b;
    @Option(names = "--remove", description = "Remove the reaction instead") boolean remove;

    @Override public void run() {
        var cx = c.open();
        var id = cx.channelId(channel);
        var link = Ref.PERMALINK.matcher(channel).find();
        var ts = link ? Ref.ts(channel) : Ref.ts(a);
        var emoji = (link ? a : b).replace(":", "");
        var r = cx.write(remove ? "reactions.remove" : "reactions.add", "channel", id, "timestamp", ts, "name", emoji);
        if (c.json) Out.json(r); else line((remove ? "removed" : "added") + " :" + emoji + ": on " + ts + " in " + cx.channelLabel(id));
    }
}
