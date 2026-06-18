package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class RenameCommand extends PolarCmd {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenameCommand.class);

    public RenameCommand() {
        super("rename", "Rename a polar world in the worlds folder");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world name", String.class);
        String newWorldName = ctx.getArgument("new world name", String.class);

        NamespacedKey worldKey = NamespacedKey.fromString(worldName, PolarPaper.getPlugin());
        World bukkitWorld = worldKey == null ? null : Bukkit.getWorld(worldKey);
        if (bukkitWorld != null) {
            PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
            if (polarGenerator == null) {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Not renaming non-polar world '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("'", NamedTextColor.RED))
                );
            } else {
                UnloadCommand.bukkitUnload(ctx, bukkitWorld).thenAccept(success -> {
                    if (success) {
                        renameWorld(ctx, worldName, newWorldName);
                    }
                });
            }
            return Command.SINGLE_SUCCESS;
        }

        renameWorld(ctx, worldName, newWorldName);

        return Command.SINGLE_SUCCESS;
    }

    private static void renameWorld(CommandContext<CommandSourceStack> ctx, String worldName, String newName) {
        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path path = worldsFolder.resolve(worldName + ".polar");
        Path newPath = worldsFolder.resolve(newName + ".polar");

        if (!Files.exists(path)) {
            ctx.getSource().getSender().sendMessage(Component.text("Couldn't find file '" + worldName + ".polar' in the worlds folder", NamedTextColor.RED));
            return;
        }

        try {
            Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING);

            LoadCommand.loadWorld(ctx, newName);

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Renamed '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("' to '", NamedTextColor.AQUA))
                            .append(Component.text(newName, NamedTextColor.AQUA))
                            .append(Component.text("'!", NamedTextColor.AQUA))
            );
        } catch (IOException e) {
            LOGGER.error("Failed to delete world: " + worldName, e);

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Failed to delete '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("'", NamedTextColor.RED))
            );
        }
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar rename <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createFileWorldNameArgument(false)
                .then(Commands.argument("new world name", StringArgumentType.greedyString())
                        .executes(RenameCommand::run)));
    }
}
