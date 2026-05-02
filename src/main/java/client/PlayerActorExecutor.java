package client;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Single-threaded actor per player — all state mutations for one player
 * execute on the same thread, eliminating lock-based synchronization.
 *
 * <pre>
 *   IO thread (MINA)              Timer thread (BUFF/MAP/WORLD)
 *        │                                │
 *        ▼                                ▼
 *   actor.execute(() -> {          actor.submit(() -> {
 *       addHP(50);                    removeCooldown(123);
 *   }); // blocks                   }); // fire-and-forget
 * </pre>
 */
public final class PlayerActorExecutor {

    private final ExecutorService executor;
    private volatile Thread actorThread;

    public PlayerActorExecutor(int playerId) {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "player-actor-" + playerId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Submit a task and block until it completes.
     * If already on the actor thread, execute inline to avoid deadlock.
     * Use for mutation methods called from IO threads.
     */
    public void execute(Runnable task) {
        if (Thread.currentThread() == actorThread) {
            task.run();
            return;
        }
        try {
            executor.submit(() -> {
                actorThread = Thread.currentThread();
                task.run();
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Actor interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * Submit a task without blocking. The calling thread returns immediately.
     * Use for timer callbacks, packet sends, and fire-and-forget operations.
     */
    public void submit(Runnable task) {
        executor.submit(() -> {
            actorThread = Thread.currentThread();
            task.run();
        });
    }

    /**
     * Shutdown this actor. After shutdown, no new tasks will be accepted.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
