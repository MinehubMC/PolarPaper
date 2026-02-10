package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.PolarGenerator;
import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(bukkitWorld.getName(), NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
            Config config = Config.readFromConfig(fileConfig, bukkitWorld);

            Location spawnPos = player.getLocation().clone();
            if (rounded) {
                spawnPos = player.getLocation().toBlockLocation();
                spawnPos.setYaw(Math.round(spawnPos.getYaw()));
                spawnPos.setPitch(Math.round(spawnPos.getPitch()));
            }

            Config newConfig = config.toBuilder().spawn(spawnPos).build();

            Config.writeToConfig(PolarPaper.getPlugin().getConfig(), bukkitWorld.getName(), newConfig);

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Set spawn for ", NamedTextColor.AQUA))
                            .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
                            .append(Component.text(" to ", NamedTextColor.AQUA))
                            .append(Component.text(newConfig.spawnString(), NamedTextColor.AQUA))
            );
        });

        return Command.SINGLE_SUCCESS;
    }

}
