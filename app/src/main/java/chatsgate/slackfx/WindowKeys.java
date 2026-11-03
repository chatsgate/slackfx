package chatsgate.slackfx;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/// The window's keys besides the function-key legend. Enter opens the chosen conversation, or sends from the write box
/// (Shift+Enter there is a new line). Esc steps back: write box, then filter, then hides the window. Up/Down move through
/// the conversations from the filter and through the messages from an empty write box; PgUp/PgDn scroll the messages.
public final class WindowKeys {

    final ChatWindow w;
    boolean shift;

    WindowKeys(ChatWindow w) { this.w = w; }

    void enter() {
        var compose = w.messages.compose;
        if (compose.isFocused() && shift) compose.insertText(compose.getCaretPosition(), "\n");
        else if (compose.isFocused()) w.send();
        else if (w.convs.selected() != null) { w.openSelected(); compose.requestFocus(); }
    }

    void escape() {
        if (w.messages.compose.isFocused()) w.filter.requestFocus();
        else if (!w.filter.getText().isEmpty()) w.filter.clear();
        else w.frame.stage.hide();
    }

    /// Scene filter: runs before the controls, so arrows and page keys mean the same thing wherever the focus is.
    void keys(KeyEvent e) {
        shift = e.isShiftDown();
        var up = e.getCode() == KeyCode.UP;
        var vertical = up || e.getCode() == KeyCode.DOWN;
        if (e.getCode() == KeyCode.PAGE_UP || e.getCode() == KeyCode.PAGE_DOWN) {
            w.messages.scroll(e.getCode() == KeyCode.PAGE_UP ? -1 : 1);
            e.consume();
        } else if (w.filter.isFocused() && vertical) {
            w.convs.move(up ? -1 : 1);
            e.consume();
        } else if (w.messages.compose.isFocused() && w.messages.compose.getText().isEmpty() && vertical) {
            w.messages.move(up ? -1 : 1);
            e.consume();
        }
    }
}
