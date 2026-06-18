package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.nio.file.Files;
import java.nio.file.Path;

public class GotoCommand extends PolarCmd {

    public GotoCommand() {
        super("goto", "Teleport to a world");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world name", String.class);

        CommandSender sender = ctx.getSource().getSender();
        Entity executor = ctx.getSource().getExecutor();
        if (!(executor instanceof Player player)) return Command.SINGLE_SUCCESS;

        World bukkitWorld = WorldKey.getWorld(worldName);
        if (bukkitWorld == null) {
            Path pluginFolder = PolarPaper.getPlugin().getDataPath();
            Path worldsFolder = pluginFolder.resolve("worlds");
            Path path = worldsFolder.resolve(worldName + ".polar");

            if (Files.exists(path)) { // world exists, just isn't loaded
                sender.sendMessage(Component.text()
                        .append(Component.text("World '", NamedTextColor.RED))
                        .append(Component.text(worldName, NamedTextColor.RED))
                        .append(Component.text("' is not loaded.", NamedTextColor.RED))
                        .appendNewline()
                        .append(Component.text("Use ", NamedTextColor.AQUA))
                        .append(Component.text()
                                        .append(Component.text("/polar load ", NamedTextColor.WHITE))
                                        .append(Component.text(worldName, NamedTextColor.WHITE))
                                        .clickEvent(ClickEvent.runCommand("/polar load " + worldName))
                                        .hoverEvent(HoverEvent.showText(Component.text("Click to load world")))
                                        .decorate(TextDecoration.UNDERLINED))
                        .append(Component.text(" to load it now", NamedTextColor.AQUA)));
            } else {
                sender.sendMessage(Component.text()
                        .append(Component.text("World '", NamedTextColor.RED))
                        .append(Component.text(worldName, NamedTextColor.RED))
                        .append(Component.text("' does not exist or is not loaded!", NamedTextColor.RED)));
            }

            return Command.SINGLE_SUCCESS;
        }

        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
            Location spawnPos;
            if (polarGenerator != null) {
                Config config = Config.readFromConfig(PolarPaper.getPlugin().getConfig(), bukkitWorld);
                spawnPos = config.spawn();
            } else {
                spawnPos = bukkitWorld.getSpawnLocation();
            }

            sender.sendMessage(
                    Component.text()
                            .append(Component.text("Teleporting to '", NamedTextColor.AQUA))
                            .append(Component.text(bukkitWorld.getKey().getKey(), NamedTextColor.AQUA))
                            .append(Component.text("'", NamedTextColor.AQUA))
            );

            spawnPos.setWorld(bukkitWorld);
            player.teleportAsync(spawnPos);
        });

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar goto <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(true, false)
            .executes(GotoCommand::run));
    }
}
