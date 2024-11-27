package dev.emortal.paperpolar.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.emortal.paperpolar.*;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("UnstableApiUsage")
public class ConvertCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        return center(ctx, false);
    }
    protected static int runCentered(CommandContext<CommandSourceStack> ctx) {
        return center(ctx, true);
    }

    private static int center(CommandContext<CommandSourceStack> ctx, boolean centered) {
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
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' already exists!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Converting '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'...", NamedTextColor.AQUA))
        );

        FileConfiguration config = Main.getPlugin().getConfig();
        Main.initWorld(newWorldName, config);

        PolarWorld newPolarWorld = new PolarWorld();

        Chunk playerChunk = player.getChunk();
        for (int x = -chunkRadius; x < chunkRadius; x++) {
            for (int z = -chunkRadius; z < chunkRadius; z++) {
                Polar.updateChunkData(
                        newPolarWorld,
                        PolarWorldAccess.DEFAULT,
                        bukkitWorld.getChunkAt(playerChunk.getX() + x, playerChunk.getZ() + z),
                        centered ? x : playerChunk.getX() + x,
                        centered ? z : playerChunk.getZ() + z
                );
            }
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Saving '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'...", NamedTextColor.AQUA))
        );

        byte[] polarBytes = PolarWriter.write(newPolarWorld);

        Path pluginFolder = Path.of(Main.getPlugin().getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");
        try {
            Files.write(worldsFolder.resolve(newWorldName + ".polar"), polarBytes);
        } catch (IOException e) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Failed to convert '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
            );

            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Saved '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'", NamedTextColor.AQUA))
        );

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Loading '", NamedTextColor.AQUA))
                        .append(Component.text(newWorldName, NamedTextColor.AQUA))
                        .append(Component.text("'...", NamedTextColor.AQUA))
        );
        Polar.loadWorld(newPolarWorld, newWorldName);
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Loaded '", NamedTextColor.AQUA))
                        .append(Component.text(newWorldName, NamedTextColor.AQUA))
                        .append(Component.text("'", NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

}
