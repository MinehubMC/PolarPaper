package live.minehub.polarpaper.paper_latest;

import live.minehub.polarpaper.core.WorldUnloader;
import live.minehub.polarpaper.core.util.TaskFutures;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

public class WorldUnloaderImpl implements WorldUnloader {
    @Override
    public CompletableFuture<Boolean> unloadWorld(Plugin plugin, World world) {
        return TaskFutures.runGlobal(plugin, () -> Bukkit.unloadWorld(world, false));
    }
}
