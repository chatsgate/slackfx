package chatsgate.slackfx.cmd;

import jarrunner.jr.apputils.UpdateResult;
import jarrunner.jr.apputils.Updater;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import chatsgate.slackfx.core.AppLink;

import java.util.concurrent.Callable;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "update", description = {"Install the newest slackfx release (the command line and the client are one program).",
    "A client that is running keeps the old version until it is restarted."})
public final class UpdateVerb implements Callable<Integer> {

    @Option(names = "--check", description = "Only say whether a newer release exists; install nothing")
    boolean check;

    @Override public Integer call() {
        var r = check ? Updater.check() : Updater.update();
        line(r.message() == null ? r.status().name() : r.message());
        if (r.status() == UpdateResult.Status.UPDATED && AppLink.live() != null) line("slackfx is running: quit it from the tray and start it again to use the new version");
        return r.status() == UpdateResult.Status.ERROR || r.status() == UpdateResult.Status.UNAVAILABLE ? 1 : 0;
    }
}
