package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "whoami", description = "Ask the service who the stored session is (proves it still works)")
public final class WhoamiVerb implements Runnable {

    @Mixin Common c;

    @Override public void run() {
        var cx = c.open();
        var r = cx.api.call("auth.test");
        if (c.json) { Out.json(r); return; }
        line("account    " + cx.session.label());
        line("user       " + r.path("user").asText() + " (" + r.path("user_id").asText() + ")");
        line("workspace  " + r.path("team").asText() + " (" + r.path("team_id").asText() + ")  " + r.path("url").asText());
    }
}
