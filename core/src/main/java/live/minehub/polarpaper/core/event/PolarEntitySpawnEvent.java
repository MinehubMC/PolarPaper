package live.minehub.polarpaper.core.event;

import live.minehub.polarpaper.core.world.PolarEntity;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

@SuppressWarnings("unused")
public class PolarEntitySpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final PolarEntity polarEntity;
    private final Entity bukkitEntity;
    private final boolean schematic;
    private final Location spawnLocation;
    private boolean cancelled;
    public PolarEntitySpawnEvent(PolarEntity polarEntity, Entity bukkitEntity, Location spawnLocation, boolean schematic) {
        this.polarEntity = polarEntity;
        this.bukkitEntity = bukkitEntity;
        this.spawnLocation = spawnLocation;
        this.schematic = schematic;
    }

    public PolarEntity getPolarEntity() {
        return polarEntity;
    }

    public Entity getBukkitEntity() {
        return bukkitEntity;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    /**
     * @return true if entity is spawned as a result of pasting a schematic
     */
    public boolean isFromSchematic() {
        return this.schematic;
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }


}