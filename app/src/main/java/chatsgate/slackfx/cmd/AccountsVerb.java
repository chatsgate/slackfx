package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "accounts", description = "List stored accounts; the active one is starred")
public final class AccountsVerb implements Runnable {

    @Override public void run() {
        var all = Store.loadSessions();
        if (all.sessions().isEmpty()) line("no accounts yet -- log in to your workspace in the browser, then run 'slackfx auth'");
        all.sessions().forEach((k, s) -> line((k.equals(all.active()) ? "* " : "  ") + s.describe()));
    }

    @Command(name = "use", description = "Make an account the active one")
    public static final class Use implements Runnable {
        @Parameters(index = "0", description = "Fragment of the account label") String fragment;

        @Override public void run() {
            var all = Store.loadSessions();
            all.active(all.match(fragment));
            Store.saveSessions(all);
            line("active  " + all.active());
        }
    }
}
