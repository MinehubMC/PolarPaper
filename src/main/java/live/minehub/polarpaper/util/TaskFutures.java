package live.minehub.polarpaper.util;

import live.minehub.polarpaper.PolarPaper;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class TaskFutures {

    public static <T> CompletableFuture<T> runAsync(Supplier<T> runnable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), _ -> {
            try {
                future.complete(runnable.get());
            } catch (Exception e) {
                ExceptionUtil.log(e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static <T> CompletableFuture<T> run(Supplier<T> runnable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            try {
                future.complete(runnable.get());
            } catch (Exception e) {
                ExceptionUtil.log(e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

}
