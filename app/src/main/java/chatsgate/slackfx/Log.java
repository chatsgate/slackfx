package chatsgate.slackfx;

import chatsgate.slackfx.core.Store;

import module java.base;

/// One log file for the resident app: ~/chatsgate/slackfx/run/slackfx.log. The app has no console, so this is
/// where everything goes. Message bodies are never logged, only what happened.
public final class Log {

    static final Path FILE = Store.home().resolve("run").resolve("slackfx.log");
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Log() { }

    public static void info(String s) { write("INFO", s, null); }

    public static void warn(String s) { write("WARN", s, null); }

    public static void error(String s, Throwable t) { write("ERROR", s, t); }

    static synchronized void write(String level, String s, Throwable t) {
        var line = STAMP.format(LocalDateTime.now()) + " " + level + " " + s + (t == null ? "" : " :: " + t) + "\n";
        if (t != null) {
            var w = new StringWriter();
            t.printStackTrace(new PrintWriter(w));
            line += w.toString().lines().limit(25).collect(Collectors.joining("\n", "", "\n"));
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.print(line);
        }
    }
}
