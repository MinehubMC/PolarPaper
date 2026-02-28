package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

        return printInfo(ctx, player.getWorld().getName());
    }

    protected static int runArg(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("worldname", String.class);
        return printInfo(ctx, worldName);
    }

    protected static int printInfo(CommandContext<CommandSourceStack> ctx, String worldName) {
        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            Component infoComponent = polarWorld.getInfoComponent(bukkitWorld);
            ctx.getSource().getSender().sendMessage(infoComponent);
        });

        return Command.SINGLE_SUCCESS;
    }

}
