package chatsgate.slackfx.core;

import com.fasterxml.jackson.databind.JsonNode;

import module java.base;
import module java.net.http;

import static chatsgate.slackfx.core.Out.M;

/// The CLI side of the write gate. When slackfx is running it holds the token and shows a branded approval, so a
/// write is sent to it rather than straight to Slack; the app performs it with its own token and returns the result.
/// The call blocks while the human decides (up to 5 minutes). If no app is running, the caller does a direct API call.
public final class GateClient {

    static final HttpClient HTTP = HttpClient.newHttpClient();

    private GateClient() { }

    public static JsonNode write(JsonNode app, String method, Map<String, Object> params) {
        try {
            var body = M.createObjectNode();
            body.put("method", method);
            body.set("params", M.valueToTree(params));
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.path("port").asInt() + "/write"))
                .header("X-Slk-Token", app.path("token").asText())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) return M.readTree(res.body());
            if (res.statusCode() == 403) throw new SlException("write refused in the slackfx approval window -- nothing was done");
            throw new SlException("slackfx gate: HTTP " + res.statusCode() + " " + Out.oneLine(res.body(), 200));
        } catch (SlException e) {
            throw e;
        } catch (Exception e) {
            throw new SlException("slackfx gate call failed: " + e.getMessage(), e);
        }
    }
}
