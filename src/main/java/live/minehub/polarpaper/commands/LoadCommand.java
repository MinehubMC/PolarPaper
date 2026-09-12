package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.source.FilePolarSource;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.nio.file.Files;
import java.nio.file.Path;

public class LoadCommand extends PolarCmd {

    public LoadCommand() {
        super("load", "Load a polar world from the worlds folder");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world path", String.class);

        loadWorld(ctx, worldName);

        return Command.SINGLE_SUCCESS;
    }

    protected static void loadWorld(CommandContext<CommandSourceStack> ctx, String worldPath) {
        Path path = WorldKey.validatePath(ctx.getSource().getSender(), worldPath);
        if (path == null) return;
        if (!Files.exists(path)) {
            ctx.getSource().getSender().sendMessage(Component.text("File '" + path.getFileName() + "' does not exist", NamedTextColor.RED));
            return;
        }

        String newWorldName = WorldKey.getWorldName(path);

        NamespacedKey worldKey = NamespacedKey.fromString(newWorldName, PolarPaper.getPlugin());
        World bukkitWorld = worldKey == null ? null : Bukkit.getWorld(worldKey);
        if (bukkitWorld != null) {
            PolarGenerator polarWorld = PolarGenerator.fromWorld(bukkitWorld);
            if (polarWorld == null) {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Non-polar world '", NamedTextColor.RED))
                                .append(Component.text(newWorldName, NamedTextColor.RED))
                                .append(Component.text("' already loaded!", NamedTextColor.RED))
                );
            } else {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("World '", NamedTextColor.RED))
                                .append(Component.text(newWorldName, NamedTextColor.RED))
                                .append(Component.text("' already loaded!", NamedTextColor.RED))
                );
            }
            return;
        }

        loadWorld(ctx, new FilePolarSource(path), newWorldName);
    }

    protected static void loadWorld(CommandContext<CommandSourceStack> ctx, PolarSource source, String newWorldName) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Loading '", NamedTextColor.GRAY))
                        .append(Component.text(newWorldName, NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );

        long before = System.nanoTime();

        Polar.createWorld(source, newWorldName).whenComplete((world, _) -> {
            boolean successful = world != null;
            if (successful) {
                int ms = (int) ((System.nanoTime() - before) / 1_000_000);

                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Loaded '", NamedTextColor.AQUA))
                                .append(Component.text(newWorldName, NamedTextColor.AQUA))
                                .append(Component.text("' in ", NamedTextColor.AQUA))
                                .append(Component.text(ms, NamedTextColor.AQUA))
                                .append(Component.text("ms. ", NamedTextColor.AQUA))
                                .append(Component.text("Click to teleport", NamedTextColor.WHITE, TextDecoration.UNDERLINED)
                                        .clickEvent(ClickEvent.runCommand("/polar goto " + newWorldName))
                                        .hoverEvent(HoverEvent.showText(Component.text()
                                                .append(Component.text("Click to run ", NamedTextColor.AQUA))
                                                .append(Component.text("/polar goto " + newWorldName)))))
                );
            } else {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Failed to load world '", NamedTextColor.RED))
                                .append(Component.text(newWorldName, NamedTextColor.RED))
                                .append(Component.text("'", NamedTextColor.RED))
                );
            }
        });
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar load <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("world path", StringArgumentType.greedyString())
                .executes(LoadCommand::run));
    }
}
