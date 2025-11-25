package live.minehub.polarpaper.event;

import live.minehub.polarpaper.PolarChunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PolarEntitySpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final PolarChunk.Entity polarEntity;
    private final Entity bukkitEntity;
    private final boolean schematic;
    private boolean cancelled;
    private Location spawnLocation;
    public PolarEntitySpawnEvent(PolarChunk.Entity polarEntity, Entity bukkitEntity, Location spawnLocation, boolean schematic) {
        this.polarEntity = polarEntity;
        this.bukkitEntity = bukkitEntity;
        this.spawnLocation = spawnLocation;
        this.schematic = schematic;
    }

    public PolarChunk.Entity getPolarEntity() {
        return polarEntity;
    }

    public Entity getBukkitEntity() {
        return bukkitEntity;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
    }

    /**
     * @return true if entity is spawned as a result of pasting a schematic
     */
    public boolean isFromSchematic() {
        return this.schematic;
    }

    @Override
    public HandlerList getHandlers() {
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