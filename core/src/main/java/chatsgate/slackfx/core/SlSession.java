package chatsgate.slackfx.core;

import datapotter.datahelper.Data;

@Data
public final class SlSession extends SlSession_A {
    String label;
    String teamId;
    String teamName;
    String teamUrl;
    String userId;
    String userName;
    String token;
    String dCookie;
    String dsCookie;
    String capturedFrom;
    Long capturedAt;

    public String domain() {
        var host = teamUrl == null ? "" : teamUrl.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        var i = host.indexOf(".slack.com");
        return i > 0 ? host.substring(0, i) : teamId;
    }

    public String describe() {
        return label + "   " + (userName == null ? "?" : userName) + " (" + userId + ")"
            + "   " + teamId + (teamName == null ? "" : " " + teamName);
    }
}
