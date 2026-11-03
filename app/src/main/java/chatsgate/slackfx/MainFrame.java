package chatsgate.slackfx;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import module java.base;
import java.lang.foreign.*;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/// The chat window's frame: an ordinary native window (resize, snap, taskbar, shadow are all Windows' own) whose title
/// bar and border are painted dark through the DWM attributes of Windows 10 20H1 and later. No glow and no band: the
/// leasegate frame is for approval windows that must catch the eye, which a chat window should not. Closing hides.
public final class MainFrame {

    static final int DARK_MODE = 20, BORDER_COLOR = 34, CAPTION_COLOR = 35, TEXT_COLOR = 36;

    final Stage stage = new Stage();

    public MainFrame(String title, List<Image> icons, int caption, int text, int border) {
        stage.setTitle(title);
        stage.getIcons().setAll(icons);
        stage.setMinWidth(640);
        stage.setMinHeight(420);
        stage.setOnCloseRequest(e -> { e.consume(); stage.hide(); });
        stage.setOnShown(e -> paint(stage, caption, text, border));
    }

    public Scene scene(Parent root) {
        var scene = new Scene(root);
        stage.setScene(scene);
        return scene;
    }

    public void toggleMaximized() { stage.setMaximized(!stage.isMaximized()); }

    /// At most maxW x maxH, and never more than 90% of the screen's work area, centred.
    public void fit(double maxW, double maxH) {
        var area = javafx.stage.Screen.getPrimary().getVisualBounds();
        var w = Math.min(maxW, area.getWidth() * 0.9);
        var h = Math.min(maxH, area.getHeight() * 0.9);
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setX(area.getMinX() + (area.getWidth() - w) / 2);
        stage.setY(area.getMinY() + (area.getHeight() - h) / 2);
    }

    /// Two minutes after the window is closed, gives the memory back, unless it was opened again meanwhile.
    public void trimWhenHidden() {
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, e -> CompletableFuture.delayedExecutor(2, TimeUnit.MINUTES)
            .execute(() -> javafx.application.Platform.runLater(() -> {
                if (!stage.isShowing()) Thread.ofVirtual().start(() -> { leasegate.core.IdleTrim.trim(); Log.info("trimmed memory after the window was closed"); });
            })));
    }

    /// 0xRRGGBB as the COLORREF Windows wants (0x00BBGGRR).
    static int colorref(int rgb) { return ((rgb & 0xff) << 16) | (rgb & 0xff00) | ((rgb >> 16) & 0xff); }

    /// Dark title bar, its colour, its text and the border. A Windows without these attributes ignores what it does not
    /// know, and any failure leaves the ordinary frame: the window must open either way.
    static void paint(Stage stage, int caption, int text, int border) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return;
        var title = stage.getTitle();
        // found by title, so for that instant the title is made unique
        var probe = title + " " + System.nanoTime();
        try (var arena = Arena.ofConfined()) {
            var linker = Linker.nativeLinker();
            var user32 = SymbolLookup.libraryLookup("user32", arena);
            var dwm = SymbolLookup.libraryLookup("dwmapi", arena);
            var find = linker.downcallHandle(user32.findOrThrow("FindWindowW"), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            var set = linker.downcallHandle(dwm.findOrThrow("DwmSetWindowAttribute"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
            stage.setTitle(probe);
            var hwnd = (MemorySegment) find.invoke(MemorySegment.NULL, arena.allocateFrom(probe, StandardCharsets.UTF_16LE));
            stage.setTitle(title);
            if (hwnd.equals(MemorySegment.NULL)) { Log.warn("dark title bar: window not found"); return; }
            attribute(set, arena, hwnd, DARK_MODE, 1);
            attribute(set, arena, hwnd, CAPTION_COLOR, colorref(caption));
            attribute(set, arena, hwnd, TEXT_COLOR, colorref(text));
            attribute(set, arena, hwnd, BORDER_COLOR, colorref(border));
        } catch (Throwable t) {
            stage.setTitle(title);
            Log.warn("dark title bar not applied: " + t);
        }
    }

    static void attribute(java.lang.invoke.MethodHandle set, Arena arena, MemorySegment hwnd, int what, int value) throws Throwable {
        var v = arena.allocate(JAVA_INT);
        v.set(JAVA_INT, 0, value);
        int hr = (int) set.invoke(hwnd, what, v, 4);
        if (hr != 0) Log.warn("DwmSetWindowAttribute " + what + " returned " + Integer.toHexString(hr));
    }
}
