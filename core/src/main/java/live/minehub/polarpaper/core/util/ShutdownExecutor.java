package live.minehub.polarpaper.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The scheduler drops any tasks while shutting down, and Folia requires certain actions to use an entity's scheduler
 * or region scheduler. Region ownership checks are bypassed when shutting down for Folia's own saving logic, so simply
 * run those tasks in the shutdown thread instead
 */
public final class ShutdownExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownExecutor.class);

    private static final Runnable WAKEUP = () -> {};

    private static final BlockingQueue<Runnable> TASKS = new LinkedBlockingQueue<>();
    private static volatile boolean running = false;

    private ShutdownExecutor() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void start() {
        running = true;
    }

    public static void stop() {
        running = false;

        Runnable task;
        while ((task = TASKS.poll()) != null) {
            run(task);
        }
    }

    public static void execute(Runnable task) {
        TASKS.add(task);
    }

    public static boolean awaitCompletion(CompletableFuture<?> future, long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);

        // Wake polling thread immediately
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
