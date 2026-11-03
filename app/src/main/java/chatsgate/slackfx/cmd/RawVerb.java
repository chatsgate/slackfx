package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import module java.base;

@Command(name = "raw", description = {"Call any Web API method: slackfx raw conversations.members channel=C123 limit=50",
    "For anything not wrapped as its own verb yet. Prints the JSON reply."})
public final class RawVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Method name, e.g. users.info") String method;
    @Parameters(index = "1..*", description = "key=value pairs") List<String> pairs = List.of();

    @Override public void run() {
        var cx = c.open();
        var p = new LinkedHashMap<String, Object>();
        pairs.forEach(kv -> {
            var i = kv.indexOf('=');
            if (i < 1) throw new SlException("'" + kv + "' is not key=value");
            p.put(kv.substring(0, i), kv.substring(i + 1));
        });
        Out.json(Api.isRead(method) ? cx.api.call(method, p) : cx.write(method, p));
    }
}
