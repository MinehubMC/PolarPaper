package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarWorld;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

@SuppressWarnings("UnstableApiUsage")
public class LoadCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("worldname", String.class);

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld != null) {
            PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
            if (polarWorld == null) {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Non-polar world '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("' already loaded!", NamedTextColor.RED))
                );
            } else {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Polar world '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("' already loaded!", NamedTextColor.RED))
                );
            }
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Loading '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'...", NamedTextColor.AQUA))
        );

        boolean successful = Polar.loadWorldConfigSource(worldName);

        if (!successful) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Failed to load world '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("'. Does it exist?", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Loaded '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'", NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

}
