package chatsgate.slackfx;

import leasegate.frame.TaskbarTheme;
import littlejlib.traynotify.BalloonSender;

import module java.base;
import module java.desktop;

/// The tray icon (open the window, quit) and the notifications. A quit stops the loopback server, so the CLI's gated
/// writes stop until slackfx is started again. AWT cannot tell a click on a notification from a click on the icon, so
/// a notification shown in the last 30 seconds is taken to be the one clicked and opens its conversation (as in wirefx).
public final class Tray {

    static final String TIP = "slackfx";
    static final long CLICK_MS = 30_000;

    final TrayIcon icon;
    final Runnable open;
    volatile Runnable last;
    volatile long lastAt;

    Tray(TrayIcon icon, Runnable open) { this.icon = icon; this.open = open; }

    public static Tray start(String account, Runnable open, Runnable quit) throws AWTException {
        var menu = new PopupMenu();
        var show = new MenuItem("Open slackfx");
        show.addActionListener(e -> open.run());
        var exit = new MenuItem("Quit slackfx (CLI writes stop)");
        exit.addActionListener(e -> quit.run());
        var sound = new CheckboxMenuItem("Sound for messages (knock)", Sound.on());
        sound.addItemListener(e -> Sound.on(sound.getState()));
        var test = new MenuItem("Test sound");
        test.addActionListener(e -> Sound.chime());
        menu.add(show);
        menu.addSeparator();
        menu.add(sound);
        menu.add(test);
        menu.addSeparator();
        menu.add(exit);
        var icon = new TrayIcon(Look.ICONS.tray(TaskbarTheme.readDark()), TIP + "  (" + account + ")", menu);
        icon.setImageAutoSize(true);
        var tray = new Tray(icon, open);
        icon.addActionListener(e -> tray.clicked());
        SystemTray.getSystemTray().add(icon);
        return tray;
    }

    void clicked() {
        var r = last;
        last = null;
        if (r != null && System.currentTimeMillis() - lastAt < CLICK_MS) r.run();
        else open.run();
    }

    /// A notification; `onClick` runs if it is clicked within 30 seconds.
    public void notify(String title, String text, Runnable onClick) {
        last = onClick;
        lastAt = System.currentTimeMillis();
        icon.displayMessage(title, text, TrayIcon.MessageType.NONE);
        BalloonSender.name("slackfx", () -> Tray.class.getResourceAsStream("slackfx-256.png"));
    }

    public void setTip(String s) { icon.setToolTip(s); }
}
