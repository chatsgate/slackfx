import module java.base;
import module java.desktop;

/// Draws the slackfx icon: an aubergine rounded-square tile (Slack's house colour) with a white "S".
/// Writes the PNGs the window and tray load (package-relative, what GateIcons expects), copies beside the
/// sources, and the exe's .ico. Run from the repository root: java app/icons/MakeIcons.java
public class MakeIcons {

    static final Color AUBERGINE = new Color(0x4A154B), AUBERGINE_DARK = new Color(0x8A3B8A), WHITE = Color.WHITE;

    public static void main(String[] a) throws Exception {
        var res = Path.of("app/src/main/resources/chatsgate/slackfx");
        var png = Path.of("app/icons/png");
        Files.createDirectories(res);
        Files.createDirectories(png);
        for (boolean dark : new boolean[] {false, true}) {
            var name = "slackfx" + (dark ? "-dark" : "");
            for (int s : new int[] {16, 24, 32, 48, 256}) write(res.resolve(name + "-" + s + ".png"), draw(s, dark));
            for (int s : new int[] {16, 24, 32, 48, 64, 128, 256}) write(png.resolve(name + "-" + s + ".png"), draw(s, dark));
        }
        Files.createDirectories(Path.of("app/src/main/jr"));
        ico(Path.of("app/src/main/jr/slackfx.ico"), new int[] {16, 24, 32, 48, 64, 128, 256});
        System.out.println("icons written");
    }

    /// Designed on a 64 x 64 grid and scaled. Tile fill is brighter on the dark variant so it reads on a dark taskbar.
    static BufferedImage draw(int size, boolean dark) {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 64.0, size / 64.0);

        var tile = new java.awt.geom.RoundRectangle2D.Double(3, 3, 58, 58, 16, 16);
        g.setColor(dark ? AUBERGINE_DARK : AUBERGINE);
        g.fill(tile);

        g.setColor(WHITE);
        var font = new Font("Segoe UI", Font.BOLD, 46);
        g.setFont(font);
        var frc = g.getFontRenderContext();
        var gv = font.createGlyphVector(frc, "S");
        var vb = gv.getVisualBounds();
        var x = 32 - vb.getWidth() / 2 - vb.getX();
        var y = 32 - vb.getHeight() / 2 - vb.getY();
        g.drawGlyphVector(gv, (float) x, (float) y);
        g.dispose();
        return img;
    }

    static void write(Path file, BufferedImage img) throws IOException { ImageIO.write(img, "png", file.toFile()); }

    /// An .ico whose pictures are PNGs (Windows reads them from Vista on).
    static void ico(Path file, int[] sizes) throws IOException {
        var pics = new ArrayList<byte[]>();
        for (int s : sizes) {
            var out = new ByteArrayOutputStream();
            ImageIO.write(draw(s, false), "png", out);
            pics.add(out.toByteArray());
        }
        var buf = ByteBuffer.allocate(6 + 16 * sizes.length + pics.stream().mapToInt(p -> p.length).sum()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            buf.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0)
                .putShort((short) 1).putShort((short) 32).putInt(pics.get(i).length).putInt(offset);
            offset += pics.get(i).length;
        }
        for (var p : pics) buf.put(p);
        Files.write(file, buf.array());
    }
}
