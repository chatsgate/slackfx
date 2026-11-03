package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import module java.base;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "members", description = "Who is in a channel")
public final class MembersVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Channel") String channel;

    @Override public void run() {
        var cx = c.open();
        var id = cx.channelId(channel);
        var ids = cx.api.pages("conversations.members", new LinkedHashMap<>(Map.of("channel", id, "limit", 1000)), "members", 100);
        if (c.json) { ids.forEach(Out::json); return; }
        line("# " + cx.channelLabel(id) + "  " + ids.size() + " members");
        ids.forEach(u -> line(u.asText() + "  " + cx.userName(u.asText())));
    }
}
