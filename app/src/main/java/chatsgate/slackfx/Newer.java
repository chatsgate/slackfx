package chatsgate.slackfx;

import jarrunner.jr.apputils.UpdateResult;

import module java.base;

/// jr calls any release that differs from the installed one "newer", so a build ahead of the published release would be
/// offered a downgrade. Only a strictly higher version (compared number by number) is offered.
final class Newer {

    private Newer() { }

    static boolean is(UpdateResult r) {
        return r.status() == UpdateResult.Status.NEWER && (r.latest() == null || r.version() == null || compare(r.latest(), r.version()) > 0);
    }

    static int compare(String a, String b) {
        var x = parts(a);
        var y = parts(b);
        for (var i = 0; i < Math.max(x.length, y.length); i++) {
            var c = Long.compare(i < x.length ? x[i] : 0, i < y.length ? y[i] : 0);
            if (c != 0) return c;
        }
        return 0;
    }

    static long[] parts(String v) {
        return Arrays.stream(v.split("[^0-9]+")).filter(s -> !s.isEmpty()).mapToLong(Long::parseLong).toArray();
    }
}
