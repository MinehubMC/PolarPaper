package live.minehub.polarpaper.canvas_latest;

import live.minehub.polarpaper.core.WorldUnloader;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

public class WorldUnloaderImpl implements WorldUnloader {
    @Override
    public CompletableFuture<Boolean> unloadWorld(Plugin plugin, World world) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            try {
                Bukkit.getServer().unloadWorldAsync(world, false, result -> {
                    future.complete(result.isSuccess());
                });
            } catch (Exception e) {
                LOGGER.error("Task failed exceptionally: ", e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
