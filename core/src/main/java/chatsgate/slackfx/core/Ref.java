package chatsgate.slackfx.core;

import module java.base;

/// Message permalinks and the times people type.
public final class Ref {

    public static final Pattern PERMALINK = Pattern.compile(
        "/archives/([A-Z0-9]+)/p(\\d{10})(\\d{6})(?:\\?(?:.*&)?thread_ts=(\\d+\\.\\d+))?");

    static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private Ref() { }

    /// The message ts inside a permalink, else the argument itself.
    public static String ts(String ref) {
        var m = PERMALINK.matcher(ref);
        return m.find() ? m.group(2) + "." + m.group(3) : ref;
    }

    /// The thread a permalink points into, when it names one.
    public static String threadTs(String ref) {
        var m = PERMALINK.matcher(ref);
        return m.find() ? m.group(4) : null;
    }

    public static String show(String ts) {
        try {
            return SHOW.format(Instant.ofEpochSecond(Long.parseLong(ts.split("\\.")[0])));
        } catch (RuntimeException e) {
            return ts;
        }
    }

    /// 2026-10-01, 2026-10-01T09:30Z, or epoch seconds, as a Slack ts bound.
    public static String parse(String t) {
        if (t == null) return null;
        if (t.matches("\\d+(\\.\\d+)?")) return t;
        try {
            return String.valueOf(LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC).toEpochSecond());
        } catch (DateTimeParseException e) {
            try {
                return String.valueOf(Instant.parse(t).getEpochSecond());
            } catch (DateTimeParseException e2) {
                throw new SlException("cannot read the time '" + t + "' -- use 2026-10-01, 2026-10-01T09:30Z or epoch seconds");
            }
        }
    }
}
