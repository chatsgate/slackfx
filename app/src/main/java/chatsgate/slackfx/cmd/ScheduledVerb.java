package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "scheduled", description = "List your scheduled messages, or cancel one with --cancel <id>")
public final class ScheduledVerb implements Runnable {

    @Mixin Common c;
    @Option(names = "--cancel", description = "Scheduled message (draft) id to cancel") String cancel;

    @Override public void run() {
        var cx = c.open();
        var all = Drafts.scheduled(cx);
        if (cancel != null) {
            var hit = all.stream().filter(d -> d.path("id").asText().equals(cancel)).findFirst()
                .orElseThrow(() -> new SlException("no scheduled message with id " + cancel));
            Drafts.cancel(cx, hit);
            line("cancelled  " + cancel);
            return;
        }
        if (c.json) { all.forEach(Out::json); return; }
        line("# " + all.size() + " scheduled");
        all.forEach(d -> line(d.path("id").asText() + "  " + Ref.show(d.path("date_scheduled").asText()) + "  "
            + cx.channelLabel(d.path("destinations").path(0).path("channel_id").asText()) + "  " + Out.oneLine(Drafts.text(d), 100)));
    }
}
