package dev.emortal.paperpolar.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.emortal.paperpolar.Config;
import dev.emortal.paperpolar.Main;
import dev.emortal.paperpolar.PolarWorld;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public class SetSpawnCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx, boolean rounded) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar setspawn (while in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        World bukkitWorld = player.getWorld();

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar setspawn (while in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        Config config = Config.readFromConfig(Main.getPlugin().getConfig(), bukkitWorld.getName());
        if (config == null) config = Config.DEFAULT;

        Config newConfig = rounded ? config.setSpawnPosRounded(player.getLocation()) : config.setSpawnPos(player.getLocation());
        if (newConfig == null) newConfig = Config.DEFAULT;

        Config.writeToConfig(Main.getPlugin().getConfig(), bukkitWorld.getName(), newConfig);

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Set spawn for ", NamedTextColor.AQUA))
                        .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" to ", NamedTextColor.AQUA))
                        .append(Component.text(newConfig.spawn(), NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

}
