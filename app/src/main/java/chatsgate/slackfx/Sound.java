package chatsgate.slackfx;

import chatsgate.slackfx.core.Store;

import module java.base;
import module java.desktop;

/// The sound of a new message: two dull wooden knocks, made in memory, or `sound.wav` in the app's home if the user drops
/// one there; `sound.off` beside it turns it off.
public final class Sound {

    static final Path OFF = Store.home().resolve("sound.off");
    static final Path CUSTOM = Store.home().resolve("sound.wav");

    private Sound() { }

    public static boolean on() { return !Files.exists(OFF); }

    public static void on(boolean on) {
        try {
            if (on) Files.deleteIfExists(OFF);
            else { Files.createDirectories(OFF.getParent()); Files.writeString(OFF, "off"); }
        } catch (IOException e) {
            Log.warn("sound setting not saved: " + e.getMessage());
        }
    }

    /// Off the calling thread, and never throws: a machine without a sound device just stays quiet.
    public static void chime() {
        if (!on()) return;
        Thread.ofVirtual().name("chime").start(() -> {
            try {
                var in = Files.isRegularFile(CUSTOM) ? AudioSystem.getAudioInputStream(CUSTOM.toFile()) : synth();
                var clip = AudioSystem.getClip();
                clip.open(in);
                clip.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) clip.close(); });
                clip.start();
            } catch (Exception | LinkageError e) {
                Log.warn("no sound: " + e);
            }
        });
    }

    /// Knock, short gap, a lower softer knock.
    static AudioInputStream synth() {
        float rate = 44_100;
        var out = new ByteArrayOutputStream();
        knock(out, rate, 620, 0.09, 0.55);
        knock(out, rate, 0, 0.05, 0);
        knock(out, rate, 470, 0.16, 0.45);
        var pcm = out.toByteArray();
        return new AudioInputStream(new ByteArrayInputStream(pcm), new AudioFormat(rate, 16, 1, true, false), pcm.length / 2);
    }

    /// A knock on wood: a tone that drops quickly to `hz` and dies within a few tens of milliseconds, with a click of
    /// noise at the start. `hz` 0 is a rest.
    static void knock(ByteArrayOutputStream out, float rate, double hz, double seconds, double volume) {
        int n = (int) (rate * seconds);
        var noise = new Random(7);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate;
            phase += 2 * Math.PI * hz * (1 + 0.6 * Math.exp(-t / 0.012)) / rate;
            double s = hz == 0 ? 0 : 0.8 * Math.sin(phase) * Math.exp(-t / 0.035) + 0.25 * (noise.nextDouble() * 2 - 1) * Math.exp(-t / 0.004);
            short v = (short) (Math.max(-1, Math.min(1, s * volume)) * Short.MAX_VALUE);
            out.write(v & 0xff);
            out.write((v >> 8) & 0xff);
        }
    }
}
