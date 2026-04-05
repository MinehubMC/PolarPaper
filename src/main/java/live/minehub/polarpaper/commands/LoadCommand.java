package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarGenerator;
import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Files;
import java.nio.file.Path;

public class LoadCommand extends PolarCmd {

    public LoadCommand() {
        super("load", "Load a polar world from the worlds folder");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("worldname", String.class);

        loadWorld(ctx, worldName);

        return Command.SINGLE_SUCCESS;
    }

    protected static void loadWorld(CommandContext<CommandSourceStack> ctx, String worldName) {
        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld != null) {
            PolarGenerator polarWorld = PolarGenerator.fromWorld(bukkitWorld);
            if (polarWorld == null) {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Non-polar world '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("' already loaded!", NamedTextColor.RED))
                );
            } else {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Polar world '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("' already loaded!", NamedTextColor.RED))
                );
            }
            return;
        }

        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path path = worldsFolder.resolve(worldName + ".polar");

        if (!Files.exists(path)) {
            ctx.getSource().getSender().sendMessage(Component.text("Couldn't find file '" + worldName + ".polar' in the worlds folder", NamedTextColor.RED));
            return;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Loading '", NamedTextColor.GRAY))
                        .append(Component.text(worldName, NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );

        Polar.loadWorldFromFile(worldName).thenAccept(world -> {
            boolean successful = world != null;
            if (successful) {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Loaded '", NamedTextColor.AQUA))
                                .append(Component.text(worldName, NamedTextColor.AQUA))
                                .append(Component.text("'. ", NamedTextColor.AQUA))
                                .append(Component.text("Use ", NamedTextColor.AQUA))
                                .append(
                                        Component.text()
                                                .append(Component.text("/polar goto ", NamedTextColor.WHITE))
                                                .append(Component.text(worldName, NamedTextColor.WHITE))
                                                .clickEvent(ClickEvent.runCommand("/polar goto " + worldName))
                                                .hoverEvent(HoverEvent.showText(Component.text("Click to run")))
                                                .decorate(TextDecoration.UNDERLINED))
                                .append(Component.text(" to teleport now", NamedTextColor.AQUA))
                );
            } else {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Failed to load world '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
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
        builder.then(createFileWorldNameArgument(true)
                .executes(LoadCommand::run));
    }
}
