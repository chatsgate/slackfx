package chatsgate.slackfx.core;

public class SlException extends RuntimeException {
    public SlException(String message) { super(message); }
    public SlException(String message, Throwable cause) { super(message, cause); }
}
