package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

        Config config = Config.readFromConfig(PolarPaper.getPlugin().getConfig(), bukkitWorld.getName());
        if (config == null) config = Config.DEFAULT;

        Location spawnPos = player.getLocation().clone();
        if (rounded) {
            spawnPos = player.getLocation().toBlockLocation();
            spawnPos.setYaw(Math.round(spawnPos.getYaw()));
            spawnPos.setPitch(Math.round(spawnPos.getPitch()));
        }

        Config newConfig = config.withSpawnPos(spawnPos);

        Config.writeToConfig(PolarPaper.getPlugin().getConfig(), bukkitWorld.getName(), newConfig);

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Set spawn for ", NamedTextColor.AQUA))
                        .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" to ", NamedTextColor.AQUA))
                        .append(Component.text(newConfig.spawnString(), NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

}
