package dev.emortal.paperpolar.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.emortal.paperpolar.Config;
import dev.emortal.paperpolar.PaperPolar;
import dev.emortal.paperpolar.PolarChunk;
import dev.emortal.paperpolar.PolarWorld;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public class InfoCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar info (while in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        World bukkitWorld = player.getWorld();

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar info (while in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        int savedEntities = 0;
        for (PolarChunk chunk : polarWorld.chunks()) {
            savedEntities += chunk.entities().size();
        }

        Config config = Config.readFromConfig(PaperPolar.getPlugin().getConfig(), bukkitWorld.getName());
        if (config == null) config = Config.DEFAULT;

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Info for ", NamedTextColor.AQUA))
                        .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
                        .append(Component.text(":", NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(Component.text(" Version: ", NamedTextColor.AQUA))
                        .append(Component.text(polarWorld.version(), NamedTextColor.AQUA))
                        .append(Component.text(" (", NamedTextColor.AQUA))
                        .append(Component.text(polarWorld.dataVersion(), NamedTextColor.AQUA))
                        .append(Component.text(")", NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(Component.text(" Compression: ", NamedTextColor.AQUA))
                        .append(Component.text(polarWorld.compression().name(), NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(Component.text(" Source: ", NamedTextColor.AQUA))
                        .append(Component.text(config.source(), NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(Component.text(" Spawn: ", NamedTextColor.AQUA))
                        .append(Component.text(config.spawn(), NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(Component.text(" Saved Entities: ", NamedTextColor.AQUA))
                        .append(Component.text(savedEntities, NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(Component.text(" Saved Chunks: ", NamedTextColor.AQUA))
                        .append(Component.text(polarWorld.chunks().size(), NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

}
