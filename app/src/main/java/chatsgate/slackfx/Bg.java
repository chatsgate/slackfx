package chatsgate.slackfx;

import module java.base;

/// One background lane for every Slack call the app makes outside the CLI gate's own request threads. Cx keeps an
/// id-to-name cache that is not thread-safe, so the window's loads and sends, and the gate's channel-name lookups,
/// all run here one at a time, and the FX thread never waits on the network.
public final class Bg {

    static final ExecutorService ONE = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("slack").factory());

    private Bg() { }

    public static void run(Runnable r) {
        ONE.execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                Log.error("background task failed", t);
            }
        });
    }

    /// Runs `c` on the lane and waits for its answer. Never call this from a task already on the lane.
    public static <T> T call(Callable<T> c) {
        try {
            return ONE.submit(c).get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
