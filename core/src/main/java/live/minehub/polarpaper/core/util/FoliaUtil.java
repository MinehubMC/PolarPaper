package live.minehub.polarpaper.core.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class FoliaUtil {

    /**
     * Runs the task on the thread owning the entity
     *
     * @param retired Run instead of the task when the entity is gone and the task can no longer be executed
     */
    public static void scheduleOnEntityIfFolia(Plugin plugin, Entity entity, Runnable runnable, Runnable retired) {
        if (ShutdownExecutor.isRunning()) {
            ShutdownExecutor.execute(runnable);
            return;
        }

        if (!isFolia() || Bukkit.isOwnedByCurrentRegion(entity)) {
            runnable.run();
            return;
        }

        // execute returns false when the entity has been removed, in which case neither callback is run
        if (!entity.getScheduler().execute(plugin, runnable, retired, 1L)) retired.run();
    }

    public static void scheduleOnRegionIfFolia(Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable) {
        if (ShutdownExecutor.isRunning()) {
            ShutdownExecutor.execute(runnable);
            return;
        }

        if (isFolia()) {
            Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, runnable);
        } else {
            runnable.run();
        }
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}
