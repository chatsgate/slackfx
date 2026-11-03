package chatsgate.slackfx;

import jarrunner.jr.apputils.JrApp;
import picocli.CommandLine.IVersionProvider;

/// `slackfx --version`: the version jr was built with, or "dev" when run from a jar.
public final class Ver implements IVersionProvider {

    @Override public String[] getVersion() { return new String[] {"slackfx " + JrApp.appVersion().orElse("dev")}; }
}
