package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.*;
import live.minehub.polarpaper.source.FilePolarSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;

public class ConvertCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        return convert(ctx, false);
    }

    protected static int runCentered(CommandContext<CommandSourceStack> ctx) {
        return convert(ctx, true);
    }

    private static int convert(CommandContext<CommandSourceStack> ctx, boolean centered) {
        CommandSender sender = ctx.getSource().getSender();
        // Being ran from console
        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

        World bukkitWorld = player.getWorld();
        String worldName = bukkitWorld.getName();

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld != null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is already converted!", NamedTextColor.RED))
                            .append(Component.newline())
                            .append(Component.text("Just use ", NamedTextColor.RED))
                            .append(Component.text("/polar save "))
                            .append(Component.text(worldName))
            );
            return Command.SINGLE_SUCCESS;
        }

        String newWorldName = ctx.getArgument("newworldname", String.class);
        Integer chunkRadius = ctx.getArgument("chunkradius", Integer.class);

        World newBukkitWorld = Bukkit.getWorld(newWorldName);
        if (newBukkitWorld != null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(newBukkitWorld.getName(), NamedTextColor.RED))
                            .append(Component.text("' already exists!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        Chunk playerChunk = player.getChunk();

        Location spawnLocation = player.getLocation().clone();
        if (centered) {
            spawnLocation.set(0, spawnLocation.getY(), 0);
        }

        long before = System.nanoTime();

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Converting '", NamedTextColor.AQUA))
                        .append(Component.text(newWorldName, NamedTextColor.AQUA))
                        .append(Component.text("'...", NamedTextColor.AQUA))
        );

        Polar.updateConfig(bukkitWorld, newWorldName);

        PolarWorld newPolarWorld = new PolarWorld();

        int worldHeight = bukkitWorld.getMaxHeight() - bukkitWorld.getMinHeight() + 1;
        int sectionCount = worldHeight / 16;

        int offsetX = centered ? 0 : playerChunk.getX();
        int offsetZ = centered ? 0 : playerChunk.getZ();
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                newPolarWorld.updateChunkAt(x + offsetX, z + offsetZ, new PolarChunk(x + offsetX, z + offsetZ, sectionCount));
            }
        }

        Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path newWorldPath = worldsFolder.resolve(newWorldName + ".polar");

        int offset2X = centered ? playerChunk.getX() : 0;
        int offset2Z = centered ? playerChunk.getZ() : 0;

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {
            Polar.saveWorld(bukkitWorld, newPolarWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, new FilePolarSource(newWorldPath), ChunkSelector.square(offsetX, offsetZ, chunkRadius), offset2X, offset2Z).thenAccept(successful -> {
                if (successful) {
                    int ms = (int) ((System.nanoTime() - before) / 1_000_000);
                    ctx.getSource().getSender().sendMessage(
                            Component.text()
                                    .append(Component.text("Done converting '", NamedTextColor.AQUA))
                                    .append(Component.text(worldName, NamedTextColor.AQUA))
                                    .append(Component.text("' in ", NamedTextColor.AQUA))
                                    .append(Component.text(ms, NamedTextColor.AQUA))
                                    .append(Component.text("ms. ", NamedTextColor.AQUA))
                                    .append(Component.text("Use ", NamedTextColor.AQUA))
                                    .append(
                                            Component.text()
                                                    .append(Component.text("/polar load ", NamedTextColor.WHITE))
                                                    .append(Component.text(newWorldName, NamedTextColor.WHITE))
                                                    .clickEvent(ClickEvent.runCommand("/polar load " + newWorldName))
                                                    .hoverEvent(HoverEvent.showText(Component.text("Click to run"))))
                                    .append(Component.text(" to load the world now", NamedTextColor.AQUA))
                    );
                } else {
                    ctx.getSource().getSender().sendMessage(
                            Component.text()
                                    .append(Component.text("Failed to convert '", NamedTextColor.RED))
                                    .append(Component.text(worldName, NamedTextColor.RED))
                    );
                    PolarPaper.getPlugin().getLogger().warning("Error while converting world " + newWorldName);
                }
            });
        });

        return Command.SINGLE_SUCCESS;
    }

}
