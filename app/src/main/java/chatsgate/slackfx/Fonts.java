package chatsgate.slackfx;

import javafx.scene.text.Font;

import module java.base;

/// Lato, the face Slack draws in (its "Slack-Lato" is a build of Lato), bundled under the SIL Open Font License, which
/// sits beside the files in fonts/OFL.txt. Loaded once, before the window is built, so the stylesheet can name it.
public final class Fonts {

    static final AtomicBoolean done = new AtomicBoolean();

    private Fonts() { }

    public static void load() {
        if (!done.compareAndSet(false, true)) return;
        for (var f : List.of("Lato-Regular", "Lato-Bold", "Lato-Black", "Lato-Italic")) {
            try (var in = Fonts.class.getResourceAsStream("fonts/" + f + ".ttf")) {
                var font = in == null ? null : Font.loadFont(in, 15);
                Log.info("font " + f + (font == null ? ": NOT loaded" : ": family '" + font.getFamily() + "', name '" + font.getName() + "'"));
            } catch (IOException e) {
                Log.warn("font " + f + ": " + e);
            }
        }
    }
}
