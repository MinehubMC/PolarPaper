package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.util.Format;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.resources.Identifier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class GotoCommand extends PolarCmd {

    private static final Logger LOGGER = LoggerFactory.getLogger(GotoCommand.class);

    public GotoCommand() {
        super("goto", "Teleport to a world");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        Identifier worldId = ctx.getArgument("world name", Identifier.class);

        CommandSender sender = ctx.getSource().getSender();
        Entity executor = ctx.getSource().getExecutor();
        if (!(executor instanceof Player player)) return Command.SINGLE_SUCCESS;

        World bukkitWorld = WorldKey.getWorld(worldId);
        if (bukkitWorld == null) {
            Path worldsFolder = PolarPaper.getWorldsPath();
            Path pathNoExtension = worldsFolder.resolve(worldId.getPath());
            Path exists = Format.findSupported(pathNoExtension);

            if (exists != null) { // world exists, just isn't loaded
                String worldName = exists.getFileName().toString();

                sender.sendMessage(Component.text()
                        .append(Component.text("World '", NamedTextColor.RED))
                        .append(Component.text(worldId.getPath(), NamedTextColor.RED))
                        .append(Component.text("' is not loaded.", NamedTextColor.RED))
                        .appendNewline()
                        .append(Component.text()
                                        .append(Component.text("Click to load ", NamedTextColor.WHITE))
                                        .append(Component.text(worldName, NamedTextColor.WHITE))
                                        .clickEvent(ClickEvent.runCommand("/polar load " + worldName))
                                        .hoverEvent(HoverEvent.showText(Component.text()
                                                .append(Component.text("Click to run ", NamedTextColor.AQUA))
                                                .append(Component.text("/polar load " + worldName))))
                                        .decorate(TextDecoration.UNDERLINED)));
            } else {
                sender.sendMessage(Component.text()
                        .append(Component.text("World '", NamedTextColor.RED))
                        .append(Component.text(worldId.getPath(), NamedTextColor.RED))
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
            player.teleportAsync(spawnPos).whenComplete((success, error) -> {
                if (error != null) {
                    LOGGER.error("Failed to teleport {} to {}", player.getName(), bukkitWorld.getKey(), error);
                    sender.sendMessage(Component.text("Failed to teleport. Check the console for details.", NamedTextColor.RED));
                } else if (!Boolean.TRUE.equals(success)) {
                    LOGGER.warn("Teleport rejected for {} to {}. Passengers: {}, vehicle: {}",
                            player.getName(), bukkitWorld.getKey(),
                            player.getPassengers().stream().map(Entity::getType).toList(),
                            player.getVehicle() == null ? null : player.getVehicle().getType());
                    sender.sendMessage(Component.text("Teleport was rejected, make sure you have no passengers. Check the console for details.", NamedTextColor.RED));
                }
            });
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
        builder.then(createWorldNameArgument(false)
            .executes(GotoCommand::run));
    }
}
