package dev.emortal.paperpolar.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
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
                            .append(Component.text("Usage: /polar info (While in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        World bukkitWorld = player.getWorld();

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar info (While in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Info for ", NamedTextColor.AQUA))
                        .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
                        .append(Component.text(":", NamedTextColor.AQUA))
                        .append(Component.newline())
                        .append(Component.text("Entities: ", NamedTextColor.AQUA))
                        .append(Component.text(bukkitWorld.getEntities().size(), NamedTextColor.AQUA))
                // TODO: finish

                // version
        );

        return Command.SINGLE_SUCCESS;
    }

}
