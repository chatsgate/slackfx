package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import chatsgate.slackfx.core.*;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "auth", description = {"Store an account. By default reads the session(s) out of a logged-in browser tab.",
    "--app imports the durable app user-token (xoxp-) from app-config.json instead: no browser, no cdpgate, no expiry.",
    "Browser capture goes through cdpgate (no debug port): the first run asks for approval in cdpgate's window.",
    "For browser capture, open https://app.slack.com in the profile first; every workspace it is signed in to is stored."})
public final class AuthVerb implements Runnable {

    @Option(names = "--app", description = "Import the durable app user-token (xoxp-) from app-config.json; no browser needed. Preferred.")
    boolean app;

    @Option(names = "--profiles", split = ",", description = "Browser profile(s) holding the workspace tab, by name or folder (cdpg profiles lists them)")
    java.util.List<String> profiles;

    @Option(names = "--team", description = "Only this workspace (fragment of its name, domain or T-id)")
    String team;

    @Option(names = "--activate", description = "Make the first captured account the active one")
    boolean activate;

    @Override public void run() {
        var got = app ? java.util.List.of(Auth.fromAppToken()) : Auth.capture(profiles, team);
        var all = Store.loadSessions();
        got.forEach(all::put);
        if (activate || app || all.active() == null) all.active(got.getFirst().label());
        Store.saveSessions(all);
        got.forEach(s -> line("stored  " + s.describe()));
        line("active  " + all.active());
        line("file    " + Store.sessionsPath());
    }
}
