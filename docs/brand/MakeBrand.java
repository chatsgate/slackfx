import module java.base;
import module java.desktop;

/// Draws the organisation avatar and the repository social preview. Run from this folder: java MakeBrand.java
public class MakeBrand {

    static final Color AUBERGINE = new Color(0x4A154B), DEEP = new Color(0x2A0A2D), LILAC = new Color(0xE3CEEB), WHITE = Color.WHITE;

    public static void main(String[] a) throws Exception {
        ImageIO.write(avatar(512), "png", new File("slackfx-512.png"));
        ImageIO.write(preview(), "png", new File("social-preview-1280x640.png"));
        System.out.println("written");
    }

    static Graphics2D g(BufferedImage img) {
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /// The app's tile (drawn on a 64 grid like the app icon), with a small "fx" for the organisation.
    static void tile(Graphics2D g, double size, boolean fx) {
        g.scale(size / 64.0, size / 64.0);
        g.setColor(AUBERGINE);
        g.fill(new java.awt.geom.RoundRectangle2D.Double(3, 3, 58, 58, 16, 16));
        g.setColor(WHITE);
        var font = new Font("Segoe UI", Font.BOLD, 46);
        var gv = font.createGlyphVector(g.getFontRenderContext(), "S");
        var vb = gv.getVisualBounds();
        g.drawGlyphVector(gv, (float) (32 - vb.getWidth() / 2 - vb.getX() - (fx ? 3 : 0)), (float) (32 - vb.getHeight() / 2 - vb.getY() - (fx ? 3 : 0)));
        if (fx) {
            g.setColor(LILAC);
            g.setFont(new Font("Segoe UI", Font.BOLD, 17));
            g.drawString("fx", 40, 56);
        }
    }

    static BufferedImage avatar(int size) {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = g(img);
        g.setColor(DEEP);
        g.fillRect(0, 0, size, size);
        g.translate(size * 0.08, size * 0.08);
        tile(g, size * 0.84, true);
        g.dispose();
        return img;
    }

    static BufferedImage preview() {
        var img = new BufferedImage(1280, 640, BufferedImage.TYPE_INT_RGB);
        var g = g(img);
        g.setPaint(new GradientPaint(0, 0, DEEP, 1280, 640, AUBERGINE));
        g.fillRect(0, 0, 1280, 640);
        var t = g.getTransform();
        g.translate(110, 160);
        tile(g, 320, false);
        g.setTransform(t);
        g.setColor(WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 104));
        g.drawString("slackfx", 500, 300);
        g.setColor(LILAC);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 38));
        g.drawString("A small chat client and command line.", 504, 372);
        g.drawString("One process. Command-line writes", 504, 426);
        g.drawString("need your approval.", 504, 476);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 28));
        g.setColor(new Color(0xB9A0C2));
        g.drawString("github.com/chatsgate/slackfx", 504, 560);
        g.dispose();
        return img;
    }
}
