package live.minehub.polarpaper.core.generator;

import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.core.world.PolarWorldAccess;
import net.kyori.adventure.builder.AbstractBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public abstract class PolarGenerator extends ChunkGenerator {
    private Config config;
    private @Nullable PolarSource source;
    private final PolarWorldAccess worldAccess;

    public PolarGenerator(Config config, @Nullable PolarSource source, PolarWorldAccess worldAccess) {
        this.config = config;
        this.source = source;
        this.worldAccess = worldAccess;
    }

    /**
     * Called after a world has been fully created, used by default in Polar to insert chunks
     */
    public abstract CompletableFuture<Void> load(@NotNull World world);

    public Config getConfig() {
        return this.config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public @Nullable PolarSource getSource() {
        return source;
    }

    public void setSource(@Nullable PolarSource source) {
        this.source = source;
    }

    public PolarWorldAccess getWorldAccess() {
        return this.worldAccess;
    }

    public abstract @Nullable PolarWorld getPolarWorld();

    public Component getInfoComponent(World world) {
        TextComponent.Builder builder = Component.text();

        builder.append(Component.text("Info for ", NamedTextColor.AQUA))
                .append(Component.text(world.getKey().getKey(), NamedTextColor.AQUA))
                .append(Component.text(":", NamedTextColor.AQUA))
                .appendNewline();

        builder.append(Component.text(" Compression: ", NamedTextColor.AQUA))
                .append(Component.text(getConfig().compression().name(), NamedTextColor.AQUA))
                .appendNewline();

        builder.append(Component.text(" Source: ", NamedTextColor.AQUA))
                .append(Component.text(getSource() == null ? "None" : getSource().getClass().getSimpleName(), NamedTextColor.AQUA));
        if (source != null) {
            builder.append(Component.text(" (", NamedTextColor.GRAY));
            long size = source.size();
            if (size < 10_000) builder.append(Component.text(size + " bytes", NamedTextColor.GRAY));
            else if (size < 10_000_000) builder.append(Component.text(size/1000 + " kB", NamedTextColor.GRAY));
            else builder.append(Component.text(size/1_000_000 + " MB", NamedTextColor.GRAY));
            builder.append(Component.text(")", NamedTextColor.GRAY));
        }
        builder.appendNewline();

        builder.append(Component.text(" Generator: ", NamedTextColor.AQUA))
                .append(Component.text(getClass().getSimpleName(), NamedTextColor.AQUA))
                .appendNewline();

        builder.append(Component.text(" Spawn: ", NamedTextColor.AQUA))
                .append(Component.text(getConfig().spawnString(), NamedTextColor.AQUA))
                .appendNewline();

        addInfoComponent(world, builder);

        return ((AbstractBuilder<TextComponent>)builder).build();
    }

    public abstract void addInfoComponent(World world, TextComponent.Builder builder);

    public abstract byte[] getUserData();

    public abstract void setUserData(byte[] userData);

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        Location loc = getConfig().spawn();
        loc.setWorld(world);
        return loc;
    }

    public static @Nullable PolarGenerator fromWorld(World world) {
        if (world == null) return null;
        ChunkGenerator generator = world.getGenerator();
        if (generator instanceof PolarGenerator polarGenerator) return polarGenerator;
        return null;
    }
}
