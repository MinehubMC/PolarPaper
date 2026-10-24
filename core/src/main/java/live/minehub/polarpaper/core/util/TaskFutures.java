package live.minehub.polarpaper.core.util;

import ca.spottedleaf.moonrise.common.util.TickThread;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class TaskFutures {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskFutures.class);
    public static final Executor VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private TaskFutures() {
    }

    public static <T extends @Nullable Object> CompletableFuture<T> runAsync(Plugin plugin, Supplier<T> runnable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getAsyncScheduler().runNow(plugin, _ -> {
            try {
                future.complete(runnable.get());
            } catch (Exception e) {
                LOGGER.error("Task failed exceptionally: ", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static <T extends @Nullable Object> CompletableFuture<T> runRegion(Plugin plugin, World world, int chunkX, int chunkZ, Supplier<T> runnable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
            try {
                future.complete(runnable.get());
            } catch (Exception e) {
                LOGGER.error("Task failed exceptionally: ", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * If already on the tick thread, immediately runs the runnable. Otherwise, schedules the task on the global region scheduler
     */
    public static <T extends @Nullable Object> CompletableFuture<T> runTickThread(Plugin plugin, Supplier<T> runnable) {
        if (TickThread.isTickThread()) return CompletableFuture.completedFuture(runnable.get());
        return runGlobal(plugin, runnable);
    }

    /**
     * Schedule the task on the global region scheduler
     */
    public static <T extends @Nullable Object> CompletableFuture<T> runGlobal(Plugin plugin, Supplier<T> runnable) {
        if (TickThread.isTickThread()) return CompletableFuture.completedFuture(runnable.get());
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                future.complete(runnable.get());
            } catch (Exception e) {
                LOGGER.error("Task failed exceptionally: ", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

}
