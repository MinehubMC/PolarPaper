package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.generator.PolarGenerator;
import live.minehub.polarpaper.util.ExceptionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeleteCommand extends PolarCmd {

    public DeleteCommand() {
        super("delete", "Delete a polar world from the worlds folder");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world name", String.class);

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld != null) {
            PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
            if (polarGenerator == null) {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Not deleting non-polar world '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("'", NamedTextColor.RED))
                );
            } else {
                UnloadCommand.bukkitUnload(ctx, bukkitWorld).thenAccept(success -> {
                    if (success) {
                        deleteWorld(ctx, worldName);
                    }
                });
            }
            return Command.SINGLE_SUCCESS;
        }

        deleteWorld(ctx, worldName);

        return Command.SINGLE_SUCCESS;
    }

    private static void deleteWorld(CommandContext<CommandSourceStack> ctx, String worldName) {
        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path path = worldsFolder.resolve(worldName + ".polar");

        if (!Files.exists(path)) {
            ctx.getSource().getSender().sendMessage(Component.text("Couldn't find file '" + worldName + ".polar' in the worlds folder", NamedTextColor.RED));
            return;
        }

        try {
            Files.delete(path);

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Deleted '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("'!", NamedTextColor.AQUA))
            );
        } catch (IOException e) {
            PolarPaper.logger().warning("Failed to delete world: " + worldName);
            ExceptionUtil.log(e);

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
                        .append(Component.text("Usage: /polar delete <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createFileWorldNameArgument(true)
                .executes(DeleteCommand::run));
    }
}
