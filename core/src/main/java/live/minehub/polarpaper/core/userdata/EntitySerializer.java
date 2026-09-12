package live.minehub.polarpaper.core.userdata;

import net.minecraft.nbt.CompoundTag;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface EntitySerializer {
    Logger LOGGER = LoggerFactory.getLogger(EntitySerializer.class);

    net.minecraft.world.entity.Entity compoundToEntity(World world, CompoundTag compound);
    byte @Nullable [] entityToBytes(Entity entity, Plugin plugin);
}
