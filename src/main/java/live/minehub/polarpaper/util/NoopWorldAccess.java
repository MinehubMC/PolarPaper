package live.minehub.polarpaper.util;

import live.minehub.polarpaper.core.world.PolarWorldAccess;
import org.bukkit.plugin.Plugin;

public class NoopWorldAccess implements PolarWorldAccess {
    private final Plugin plugin;
    public NoopWorldAccess(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }
}
