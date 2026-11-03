package chatsgate.slackfx;

import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import module java.base;

/// The window as JavaFX itself draws it, saved as a PNG. A screen capture cannot be trusted for this: another window
/// can sit on top (an always-on-top, elevated window does), a hardware-drawn JavaFX window reads back black
/// through PrintWindow, and DPI scaling skews coordinates. Rendering the scene sidesteps all three. Call on the FX thread.
public final class Snap {

    private Snap() { }

    public static Path png(Scene scene, Path out) throws IOException {
        var img = scene.snapshot(null);
        var w = (int) img.getWidth();
        var h = (int) img.getHeight();
        var px = new int[w * h];
        img.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        var bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, w, h, px, 0, w);
        Files.createDirectories(out.getParent());
        ImageIO.write(bi, "png", out.toFile());
        return out;
    }
}
