package live.minehub.polarpaper.core.util;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class FoliaUtil {

    public static void scheduleOnEntityIfFolia(Entity entity, Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            entity.getScheduler().execute(plugin, runnable, null, 1L);
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
