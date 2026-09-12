package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.resources.Identifier;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
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
        String worldPath = ctx.getArgument("world name", String.class);
        Path path = WorldKey.validatePath(ctx.getSource().getSender(), worldPath);
        if (path == null) return Command.SINGLE_SUCCESS;
        if (!Files.exists(path)) {
            ctx.getSource().getSender().sendMessage(Component.text("File '" + path.getFileName() + "' does not exist", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String oldName = WorldKey.getWorldName(path);
        String newWorldName = ctx.getArgument("new world name", String.class);
        if (WorldKey.validatePath(ctx.getSource().getSender(), newWorldName) == null) return Command.SINGLE_SUCCESS;

        World bukkitWorld = WorldKey.getWorld(oldName);
        if (bukkitWorld != null) {
            PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
            if (polarGenerator == null) {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Not renaming non-polar world '", NamedTextColor.RED))
                                .append(Component.text(bukkitWorld.getWorldPath().toString(), NamedTextColor.RED))
                                .append(Component.text("'", NamedTextColor.RED))
                );
            } else {
                UnloadCommand.bukkitUnload(ctx, bukkitWorld).thenAccept(success -> {
                    if (success) {
                        renameWorld(ctx, path, oldName, newWorldName);
                    }
                });
            }
            return Command.SINGLE_SUCCESS;
        }

        renameWorld(ctx, path, oldName, newWorldName);

        return Command.SINGLE_SUCCESS;
    }

    private static void renameWorld(CommandContext<CommandSourceStack> ctx, Path worldPath, String oldName, String newName) {
        Path newPath = WorldKey.validatePath(ctx.getSource().getSender(), newName);
        if (newPath == null) return;

        try {
            Files.move(worldPath, newPath, StandardCopyOption.REPLACE_EXISTING);

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Renamed '", NamedTextColor.AQUA))
                            .append(Component.text(oldName, NamedTextColor.AQUA))
                            .append(Component.text("' to '", NamedTextColor.AQUA))
                            .append(Component.text(newName, NamedTextColor.AQUA))
                            .append(Component.text("'!", NamedTextColor.AQUA))
            );
        } catch (IOException e) {
            LOGGER.error("Failed to rename world: " + oldName, e);

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Failed to rename '", NamedTextColor.RED))
                            .append(Component.text(oldName, NamedTextColor.RED))
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
