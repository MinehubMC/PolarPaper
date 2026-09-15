package live.minehub.polarpaper.util;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

public class WorldEditUtil {

    public static @Nullable Selection getPlayerSelection(Player player) {
        BukkitPlayer wePlayer = BukkitAdapter.adapt(player);
        LocalSession weSession = WorldEdit.getInstance().getSessionManager().get(wePlayer);

        if (!weSession.isSelectionDefined(wePlayer.getWorld())) return null;

        try {
            Region selection = weSession.getSelection(wePlayer.getWorld());
            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();
            return new Selection(
                    new Vector3i(min.x(), min.y(), min.z()),
                    new Vector3i(max.x(), max.y(), max.z())
            );
        } catch (IncompleteRegionException e) {
            return null;
        }
    }

}
