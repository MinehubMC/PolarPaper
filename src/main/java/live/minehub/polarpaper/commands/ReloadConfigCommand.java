package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class ReloadConfigCommand extends PolarCmd {

    public ReloadConfigCommand() {
        super("reloadconfig", "Reload the config");
    }

    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        PolarPaper.getPlugin().reloadConfig();

        int numWorlds = 0;
        for (World bukkitWorld : Bukkit.getWorlds()) {
            if (!Config.isInConfig(bukkitWorld.getKey().getKey())) continue;

            Polar.reloadConfig(bukkitWorld);

            numWorlds++;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Reloaded config for ", NamedTextColor.AQUA))
                        .append(Component.text(numWorlds, NamedTextColor.AQUA))
                        .append(Component.text(" worlds", NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {

    }
}

