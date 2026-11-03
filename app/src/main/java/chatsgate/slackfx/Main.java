package chatsgate.slackfx;

import module java.base;

/// The one entry point. No arguments starts the client; `--background` starts it in the tray only; anything else
/// (a verb, --help, --version) is the command line. `--demo` starts the client on an invented workspace with its own
/// state folder, for trying it out and for screenshots.
public final class Main {

    private Main() { }

    public static void main(String[] args) throws Exception {
        var rest = new ArrayList<>(List.of(args));
        if (rest.remove("--demo")) demo();
        if (rest.isEmpty() || rest.equals(List.of("--background")) || Boolean.getBoolean("slackfx.demo")) SlackFx.main(rest.toArray(String[]::new));
        else Cli.main(args);
    }

    static void demo() throws IOException {
        var home = Path.of(System.getProperty("user.home"), "chatsgate", "slackfx-demo");
        System.setProperty("slackfx.demo", "true");
        System.setProperty("slackfx.home", home.toString());
        Files.createDirectories(home);
        if (!Files.exists(home.resolve("app-config.json"))) Files.writeString(home.resolve("app-config.json"), "{}");
    }
}
