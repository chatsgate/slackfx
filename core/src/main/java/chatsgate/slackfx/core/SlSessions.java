package chatsgate.slackfx.core;

import datapotter.datahelper.Data;

import module java.base;

@Data
public final class SlSessions extends SlSessions_A {
    String active;
    Map<String, SlSession> sessions = new LinkedHashMap<>();

    public Optional<SlSession> resolve(String wanted) {
        if (sessions.isEmpty()) return Optional.empty();
        var key = wanted != null ? match(wanted) : active;
        if (key == null) return Optional.of(sessions.values().iterator().next());
        return Optional.ofNullable(sessions.get(key));
    }

    public String match(String fragment) {
        if (sessions.containsKey(fragment)) return fragment;
        var hits = sessions.keySet().stream().filter(k -> k.contains(fragment)).toList();
        if (hits.size() == 1) return hits.getFirst();
        if (hits.isEmpty()) throw new SlException(
            "no stored account matching '" + fragment + "'. Known: " + String.join(", ", sessions.keySet()));
        throw new SlException("'" + fragment + "' matches more than one account: " + String.join(", ", hits));
    }

    public SlSessions put(SlSession s) {
        var stale = sessions.entrySet().stream()
            .filter(e -> same(e.getValue(), s)).map(Map.Entry::getKey).toList();
        var hadActive = stale.contains(active);
        stale.forEach(sessions::remove);
        var key = s.domain() + (s.dCookie() == null ? "-app" : "");
        if (sessions.containsKey(key)) key = key + "-" + s.userName();
        s.label(key);
        sessions.put(key, s);
        if (active == null || hadActive) active = key;
        return this;
    }

    /// Same account AND same credential kind: a durable app token (no cookie) and a browser session for the
    /// one workspace coexist as separate entries, so re-capturing one never evicts the other.
    static boolean same(SlSession a, SlSession b) {
        return Objects.equals(a.teamId(), b.teamId()) && Objects.equals(a.userId(), b.userId())
            && (a.dCookie() == null) == (b.dCookie() == null);
    }
}
