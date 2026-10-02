package live.minehub.polarpaper.core.generator;

import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.userdata.WorldUserData;
import live.minehub.polarpaper.core.util.TaskFutures;
import live.minehub.polarpaper.core.world.NoUnloadLevelChunk;
import live.minehub.polarpaper.core.world.PolarChunk;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.core.world.PolarWorldAccess;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Prefer PolarStreamLoader if possible for a lower memory spike when loading
 */
public class PolarWorldLoader extends PolarGenerator {
    private byte[] userData = new byte[0];
    private final @NotNull PolarWorld polarWorld;
    public PolarWorldLoader(@NotNull Config config, @NotNull PolarWorld polarWorld, @NotNull PolarWorldAccess worldAccess) {
        super(config, null, worldAccess);
        this.polarWorld = polarWorld;
    }

    //https://github.com/hollow-cube/polar/blob/main/src/main/java/net/hollowcube/polar/StreamingPolarLoader.java#L64
    public CompletableFuture<Void> load(@NotNull World world) {
        ServerLevel level = ((CraftWorld) world).getHandle();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (PolarChunk chunk : polarWorld.chunks()) {
            NoUnloadLevelChunk levelChunk = chunk.createLevelChunk(level);

            futures.add(TaskFutures.runTickThread(getWorldAccess().getPlugin(), () -> {
                for (PolarChunk.BlockEntity blockEntity : chunk.blockEntities()) {
                    ChunkUtils.addBlockEntity(blockEntity, levelChunk);
                }
                ChunkUtils.insertChunk(level, levelChunk);
                getWorldAccess().loadChunkData(world, levelChunk, chunk.userData());
                return null;
            }));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }


    @Override
    public @Nullable PolarWorld getPolarWorld() {
        return this.polarWorld;
    }

    @Override
    public void addInfoComponent(World world, TextComponent.Builder builder) {
        Vector3i offset = WorldUserData.readSchematicOffset(userData);

        builder.append(Component.text(" Version: ", NamedTextColor.AQUA))
                .append(Component.text(polarWorld.version(), NamedTextColor.AQUA))
                .append(Component.text(" (", NamedTextColor.AQUA))
                .append(Component.text(polarWorld.dataVersion(), NamedTextColor.AQUA))
                .append(Component.text(")", NamedTextColor.AQUA));

        if (offset != null) {
            builder.appendNewline();
            builder.append(Component.text(" Schematic center: ", NamedTextColor.AQUA));
            builder.append(Component.text(offset.x + ", " + offset.y + ", " + offset.z, NamedTextColor.AQUA));
        }
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    public byte[] getUserData() {
        return userData;
    }

    public void setUserData(byte[] userData) {
        this.userData = userData;
    }



}
