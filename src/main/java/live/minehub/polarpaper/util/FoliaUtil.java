package live.minehub.polarpaper.util;


import live.minehub.polarpaper.PolarPaper;
import org.bukkit.entity.Entity;

public class FoliaUtil {

    public static void scheduleOnEntityIfFolia(Entity entity, Runnable runnable) {
        if (isFolia()) {
            entity.getScheduler().execute(PolarPaper.getPlugin(), runnable, null, 1L);
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
