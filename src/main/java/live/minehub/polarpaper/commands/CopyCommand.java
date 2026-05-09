package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.*;
import live.minehub.polarpaper.generator.PolarGenerator;
import live.minehub.polarpaper.source.FilePolarSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

public class CopyCommand extends PolarCmd {

    public CopyCommand() {
        super("copy", "Copy a polar world");
    }

    public static int run(CommandContext<CommandSourceStack> ctx) {
        // TODO: Copy with paths like load world

        CommandSender sender = ctx.getSource().getSender();

        String worldName = ctx.getArgument("world name", String.class);
        String newWorldPath = ctx.getArgument("new world path", String.class);

        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path path = worldsFolder.resolve(newWorldPath + (newWorldPath.endsWith(".polar") ? "" : ".polar"));

        if (Files.exists(path)) {
            sender.sendMessage(Component.text("File '" + path.getFileName() + "' already exists", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        if (!path.normalize().startsWith(worldsFolder)) {
            sender.sendMessage(Component.text("Outside of worlds folder", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String newWorldName = path.getFileName().toString().replaceAll(".polar$", "");

        NamespacedKey worldKey = NamespacedKey.fromString(worldName, PolarPaper.getPlugin());
        World bukkitWorld = worldKey == null ? null : Bukkit.getWorld(worldKey);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config.writeToConfig(fileConfig, worldName, polarGenerator.getConfig());
        PolarWorld polarWorld = PolarWorld.convert(bukkitWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, BlockSelector.ALL, polarGenerator.getConfig());

        Polar.createWorld(polarWorld, newWorldName).thenAccept(world -> {
            if (world == null) {
                sender.sendMessage(Component.text("Failed to copy world", NamedTextColor.RED));
                return;
            }

            PolarGenerator generator = PolarGenerator.fromWorld(world);
            if (generator != null) generator.setSource(new FilePolarSource(path)); // change source to the path so it can be saved/autosaved properly

            sender.sendMessage(
                    Component.text()
                            .append(Component.text("Copied '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("' to '", NamedTextColor.AQUA))
                            .append(Component.text(newWorldName, NamedTextColor.AQUA))
                            .append(Component.text("'. ", NamedTextColor.AQUA))
                            .append(Component.text("Click to teleport", NamedTextColor.WHITE, TextDecoration.UNDERLINED)
                                    .clickEvent(ClickEvent.runCommand("/polar goto " + worldName))
                                    .hoverEvent(HoverEvent.showText(Component.text()
                                            .append(Component.text("Click to run ", NamedTextColor.AQUA))
                                            .append(Component.text("/polar goto " + worldName)))))
            );
        });

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar copy <world name> <new world path> to copy a polar world", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(false, true)
                .then(Commands.argument("new world path", StringArgumentType.greedyString())
                        .executes(CopyCommand::run)));
    }
}
