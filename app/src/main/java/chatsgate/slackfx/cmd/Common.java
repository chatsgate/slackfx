package chatsgate.slackfx.cmd;

import picocli.CommandLine.Option;
import chatsgate.slackfx.core.Cx;

public final class Common {

    @Option(names = {"-a", "--account"},
        description = "Which stored account to use (any unambiguous fragment of its label); default is the active one")
    String account;

    @Option(names = "--json", description = "Print the raw response instead of readable lines")
    boolean json;

    public Cx open() { return Cx.open(account); }
}
