package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "mark", description = "Mark a channel as read, up to a message (default: the latest). Changes your unread state.")
public final class MarkVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Channel, or a message permalink") String channel;
    @Parameters(index = "1", arity = "0..1", description = "Message ts to mark up to") String ts;

    @Override public void run() {
        var cx = c.open();
        var id = cx.channelId(channel);
        var upTo = ts != null ? Ref.ts(ts) : Ref.PERMALINK.matcher(channel).find() ? Ref.ts(channel)
            : cx.api.call("conversations.history", "channel", id, "limit", 1).path("messages").path(0).path("ts").asText();
        cx.write("conversations.mark", "channel", id, "ts", upTo);
        line("marked " + cx.channelLabel(id) + " read up to " + upTo);
    }
}
