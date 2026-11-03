package chatsgate.slackfx.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import module java.base;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "file", description = {"Download a file, or read a canvas, by file id (F...) or its URL.",
    "Saves into the current folder under its own name; --out - prints it instead (how to read a canvas)."})
public final class FileVerb implements Runnable {

    static final Pattern FILE_ID = Pattern.compile("\\bF[A-Z0-9]{8,}\\b");

    @Mixin Common c;
    @Parameters(index = "0", description = "File id or a URL containing it") String ref;
    @Option(names = "--out", description = "Where to save it; '-' prints to the terminal") String out;

    @Override public void run() {
        var cx = c.open();
        var m = FILE_ID.matcher(ref);
        if (!m.find()) throw new SlException("no file id (F...) in '" + ref + "'");
        var f = cx.api.call("files.info", "file", m.group()).path("file");
        if (c.json) { Out.json(f); return; }
        var url = f.path("url_private_download").asText(f.path("url_private").asText(""));
        if (url.isBlank()) throw new SlException("this file has no downloadable content");
        var res = cx.api.download(url);
        if (res.statusCode() != 200) throw new SlException("download answered HTTP " + res.statusCode());
        if ("-".equals(out)) { System.out.write(res.body(), 0, res.body().length); System.out.flush(); return; }
        var name = f.path("name").asText(f.path("title").asText(m.group()));
        var target = Path.of(out != null ? out : name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        try {
            Files.write(target, res.body());
        } catch (java.io.IOException e) {
            throw new SlException("cannot write " + target, e);
        }
        line("saved  " + target.toAbsolutePath() + "  (" + res.body().length + " bytes, " + f.path("filetype").asText("?") + ")");
    }
}
