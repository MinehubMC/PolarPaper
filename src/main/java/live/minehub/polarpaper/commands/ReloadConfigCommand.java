package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class ReloadConfigCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        PolarPaper.getPlugin().reloadConfig();

        for (World bukkitWorld : Bukkit.getWorlds()) {
            if (!Polar.isInConfig(bukkitWorld.getName())) continue;

            PolarWorld world = PolarWorld.fromWorld(bukkitWorld);
            if (world == null) continue;
            PolarGenerator generator = PolarGenerator.fromWorld(bukkitWorld);
            if (generator == null) continue;

            Config config = Config.readFromConfig(PolarPaper.getPlugin().getConfig(), bukkitWorld.getName());
            if (config == null) continue;

            Polar.updateWorldConfig(bukkitWorld, config);

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Reloaded config for ", NamedTextColor.AQUA))
                            .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
            );
        }

        return Command.SINGLE_SUCCESS;
    }

}

