package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.util.Map;

public class ReloadConfigCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        for (World bukkitWorld : Bukkit.getWorlds()) {
            if (!Polar.isInConfig(bukkitWorld.getName())) continue;

            PolarWorld world = PolarWorld.fromWorld(bukkitWorld);
            if (world == null) continue;
            PolarGenerator generator = PolarGenerator.fromWorld(bukkitWorld);
            if (generator == null) continue;

            PolarPaper.getPlugin().reloadConfig();

            Config config = Config.readFromConfig(PolarPaper.getPlugin().getConfig(), bukkitWorld.getName());
            if (config == null) continue;

            generator.setConfig(config);

            bukkitWorld.setDifficulty(config.difficulty());
            bukkitWorld.setPVP(config.pvp());
            bukkitWorld.setSpawnFlags(config.allowMonsters(), config.allowAnimals());
            bukkitWorld.setAutoSave(config.autoSave());

            for (Map<String, ?> gamerule : config.gamerules()) {
                for (Map.Entry<String, ?> entry : gamerule.entrySet()) {
                    GameRule<?> rule = GameRule.getByName(entry.getKey());
                    if (rule == null) continue;
                    setGameRule(bukkitWorld, rule, entry.getValue());
                }
            }

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Reloaded config for ", NamedTextColor.AQUA))
                            .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private static <T> void setGameRule(World world, GameRule<?> rule, Object value) {
        world.setGameRule((GameRule<T>) rule, (T)value);
    }
}

