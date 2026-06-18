package live.minehub.polarpaper.core.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class TaskFutures {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskFutures.class);

    private TaskFutures() {
    }

    public static <T> CompletableFuture<T> runAsync(Plugin plugin, Supplier<T> runnable) {
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

    public static <T> CompletableFuture<T> run(Plugin plugin, Supplier<T> runnable) {
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
