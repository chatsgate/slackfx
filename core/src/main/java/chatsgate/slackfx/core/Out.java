package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Out {

    public static final ObjectMapper M = new ObjectMapper();

    private Out() { }

    public static void json(JsonNode n) {
        try {
            System.out.println(M.writerWithDefaultPrettyPrinter().writeValueAsString(n));
        } catch (Exception e) {
            throw new SlException("cannot print json", e);
        }
    }

    public static void line(String s) { System.out.println(s); }

    public static void err(String s) { System.err.println(s); }

    public static String text(JsonNode n, String field) {
        var v = n.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    public static String oneLine(String s, int max) {
        var t = s.replace("\r", " ").replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
