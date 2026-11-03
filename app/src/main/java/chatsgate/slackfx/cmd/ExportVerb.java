package chatsgate.slackfx.cmd;

import com.fasterxml.jackson.databind.JsonNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import chatsgate.slackfx.core.*;

import module java.base;

import static chatsgate.slackfx.core.Out.line;

@Command(name = "export", description = {"Save a whole channel to a folder: every message, every thread reply, every attached file.",
    "Writes messages.jsonl (raw, one message per line, replies under replies_full), messages.md (readable) and files/.",
    "Re-running into the same folder rewrites the messages and skips files already saved."})
public final class ExportVerb implements Runnable {

    @Mixin Common c;
    @Parameters(index = "0", description = "Channel: id, #name, @person or a message permalink") String channel;
    @Option(names = "--out", description = "Folder to write (default: ./slack-<channel>)") Path out;
    @Option(names = "--since", description = "Only after this time: 2026-10-01, 2026-10-01T09:30Z or epoch seconds") String since;
    @Option(names = "--until", description = "Only before this time") String until;
    @Option(names = "--no-replies", description = "Skip thread replies") boolean noReplies;
    @Option(names = "--no-files", description = "Skip downloading attached files") boolean noFiles;

    @Override public void run() {
        var cx = c.open();
        var id = cx.channelId(channel);
        var label = cx.channelLabel(id);
        var dir = out != null ? out : Path.of("slack-" + label.replaceAll("[^A-Za-z0-9_.-]", ""));
        Out.err("reading " + label + " (" + id + ")...");
        var msgs = Fetch.messages(cx, id, Ref.parse(since), Ref.parse(until), 0, !noReplies);
        var files = noFiles ? Map.<String, String>of() : Attach.save(cx, msgs, dir.resolve("files"));
        write(cx, dir, label, msgs, files);
        var replies = msgs.stream().mapToInt(m -> m.path("replies_full").size()).sum();
        line("channel   " + label + " (" + id + ")");
        line("messages  " + msgs.size() + " (+ " + replies + " thread replies)");
        line("files     " + files.size() + (noFiles ? " (skipped)" : " saved in " + dir.resolve("files")));
        if (!msgs.isEmpty()) line("range     " + Ref.show(msgs.getFirst().path("ts").asText()) + "  to  " + Ref.show(msgs.getLast().path("ts").asText()));
        line("written   " + dir.toAbsolutePath());
    }

    void write(Cx cx, Path dir, String label, List<JsonNode> msgs, Map<String, String> files) {
        try {
            Files.createDirectories(dir);
            var raw = new StringBuilder();
            var md = new StringBuilder("# " + label + "  (exported " + Instant.now() + ")\n\n");
            for (var m : msgs) {
                raw.append(Out.M.writeValueAsString(m)).append('\n');
                md.append(Render.withReplies(cx, m)).append("\n");
                files.forEach((fid, name) -> { if (hasFile(m, fid)) md.append("    saved: files/").append(name).append("\n"); });
                md.append("\n");
            }
            Files.writeString(dir.resolve("messages.jsonl"), raw.toString());
            Files.writeString(dir.resolve("messages.md"), md.toString());
        } catch (java.io.IOException e) {
            throw new SlException("cannot write into " + dir, e);
        }
    }

    static boolean hasFile(JsonNode m, String fid) {
        for (var f : m.path("files")) if (fid.equals(f.path("id").asText())) return true;
        for (var r : m.path("replies_full")) for (var f : r.path("files")) if (fid.equals(f.path("id").asText())) return true;
        return false;
    }
}
