package live.minehub.polarpaper.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Runs tasks that would normally be handed to a region or entity scheduler on the thread that is stopping the server.
 * <p>
 * Plugins are disabled while the server is stopping, and a scheduler drops anything submitted by a plugin that is no
 * longer enabled. Folia goes further and halts its region schedulers before it disables plugins, so a task scheduled
 * from onDisable is never picked up and whatever waits on it blocks forever. Folia disables plugins from its region
 * shutdown thread, which bypasses the region ownership checks (it is the same thread Folia saves its own chunks with),
 * so those tasks can be run there instead.
 */
public final class ShutdownExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownExecutor.class);

    private static final Runnable WAKEUP = () -> {};

    private static final BlockingQueue<Runnable> TASKS = new LinkedBlockingQueue<>();
    private static volatile boolean running = false;

    private ShutdownExecutor() {
    }

    /**
     * Whether a thread is currently picking up the tasks given to {@link #execute(Runnable)}
     */
    public static boolean isRunning() {
        return running;
    }

    /**
     * Starts accepting tasks on the calling thread.
     * <br>
     * The caller is expected to run them through {@link #awaitCompletion} and to call {@link #stop()} when done
     */
    public static void start() {
        running = true;
    }

    /**
     * Stops accepting tasks and runs what is left over, so nothing waiting on a queued task stays blocked
     */
    public static void stop() {
        running = false;

        Runnable task;
        while ((task = TASKS.poll()) != null) {
            run(task);
        }
    }

    /**
     * Queues a task to be run by the thread stopping the server
     */
    public static void execute(Runnable task) {
        TASKS.add(task);
    }

    /**
     * Runs queued tasks on the calling thread until the given future completes
     *
     * @return false if the future did not complete within the timeout
     */
    public static boolean awaitCompletion(CompletableFuture<?> future, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);

        // the future is completed by whichever thread finishes the work, so wake the loop up rather than
        // leaving it waiting on a queue that nothing is going to add to
        future.whenComplete((_, _) -> TASKS.add(WAKEUP));

        while (!future.isDone()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;

            Runnable task;
            try {
                task = TASKS.poll(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            if (task != null) run(task);
        }

        return true;
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            LOGGER.error("Shutdown task failed: ", e);
        }
    }

}
