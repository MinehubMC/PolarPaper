package dev.emortal.paperpolar.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.emortal.paperpolar.Polar;
import dev.emortal.paperpolar.PolarWorld;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

@SuppressWarnings("UnstableApiUsage")
public class SaveCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("worldname", String.class);

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Saving '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'...", NamedTextColor.AQUA))
        );
        boolean successful = Polar.saveWorldConfigSource(bukkitWorld);
        if (successful) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Saved '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("'", NamedTextColor.AQUA))
            );
        } else {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Something went wrong while trying to save '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("'", NamedTextColor.RED))
            );
        }

        return Command.SINGLE_SUCCESS;
    }

}
