package chatsgate.slackfx;

import leasegate.frame.Brand;
import leasegate.frame.GateIcons;

/// slackfx's colour and icons, shared by the tray and every window it frames. Aubergine is Slack's house colour,
/// so the tile, the tray and the approval band read as one thing. Icons resolve package-relative to this class:
/// resources/chatsgate/slackfx/slackfx-{size}.png (+ -dark- for a dark taskbar).
public final class Look {

    public static final String COLOR = "#4A154B";

    public static final GateIcons ICONS = new GateIcons(Look.class, "slackfx");

    public static final Brand BRAND = new Brand("slackfx", COLOR, ICONS);

    private Look() { }
}
