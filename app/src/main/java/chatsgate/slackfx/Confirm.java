package chatsgate.slackfx;

import leasegate.frame.GateFrame;

import module java.base;

/// The write-approval box: a yes/no that no stray keystroke can answer. It is quiet for two seconds, then yes is
/// F2 and no is Esc — never a letter, Space or Enter. Shown for every CLI write (send/edit/delete/react) so the
/// durable token, which acts as the user, never acts without a deliberate approval. Runs on the JavaFX thread.
public final class Confirm {

    private Confirm() { }

    public static boolean ask(String title, String message, String yes, String no) {
        return Fx.call(() -> GateFrame.ask(Look.BRAND, title, message, yes, no));
    }
}
