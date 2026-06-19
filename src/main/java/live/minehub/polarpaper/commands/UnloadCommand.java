package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.util.FoliaUtil;
import live.minehub.polarpaper.core.util.TaskFutures;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.resources.Identifier;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

public class UnloadCommand extends PolarCmd {

    public UnloadCommand() {
        super("unload", "Unload a polar world");
    }

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        Identifier worldId = ctx.getArgument("world name", Identifier.class);
        unload(ctx, worldId, false, false);
        return Command.SINGLE_SUCCESS;
    }

    protected static int runOverrided(CommandContext<CommandSourceStack> ctx) {
        Identifier worldId = ctx.getArgument("world name", Identifier.class);
        Boolean override = ctx.getArgument("save", Boolean.class);
        unload(ctx, worldId, true, override);
        return Command.SINGLE_SUCCESS;
    }

    protected static CompletableFuture<Boolean> unload(CommandContext<CommandSourceStack> ctx, Identifier worldId, boolean saveOverrided, boolean save) {
        if (FoliaUtil.isFolia()) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Unloading worlds is not supported on Folia!", NamedTextColor.RED))
            );
            return CompletableFuture.completedFuture(false);
        }

        World bukkitWorld = WorldKey.getWorld(worldId);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldId.getPath(), NamedTextColor.RED))
                            .append(Component.text("' already not loaded!", NamedTextColor.RED))
            );
            return CompletableFuture.completedFuture(false);
        }

        PolarGenerator generator = PolarGenerator.fromWorld(bukkitWorld);
        if (generator == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldId.getPath(), NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return CompletableFuture.completedFuture(false);
        }
        Config config = generator.getConfig();

        boolean shouldSave;
        if (saveOverrided) {
            shouldSave = save;
        } else {
            shouldSave = config.autoSaveIntervalTicks() != -1 || config.saveOnStop();
        }

        if (shouldSave) {
            return SaveCommand.saveWorld(ctx, worldId).thenCompose(success -> {
                if (!success) return CompletableFuture.completedFuture(false);
                return bukkitUnload(ctx, bukkitWorld);
            });
        } else {
            if (saveOverrided) {
                ctx.getSource().getSender().sendMessage(Component.text("Save force disabled, world will not be saved before unload", NamedTextColor.AQUA));
            } else {
                ctx.getSource().getSender().sendMessage(Component.text("Autosave and save on stop is disabled, world will not be saved before unload", NamedTextColor.AQUA));
            }

            return bukkitUnload(ctx, bukkitWorld);
        }
    }

    protected static CompletableFuture<Boolean> bukkitUnload(CommandContext<CommandSourceStack> ctx, World bukkitWorld) {
        String worldName = bukkitWorld.getKey().getKey();
        return TaskFutures.run(PolarPaper.getPlugin(), () -> {
            return Bukkit.unloadWorld(bukkitWorld, false);
        }).handle((success, ex) -> {
            if (!success || ex != null) {
                if (!bukkitWorld.getPlayers().isEmpty()) {
                    ctx.getSource().getSender().sendMessage(
                            Component.text()
                                    .append(Component.text("There are still players in '", NamedTextColor.RED))
                                    .append(Component.text(worldName, NamedTextColor.RED))
                                    .append(Component.text("'", NamedTextColor.RED))
                    );
                    return false;
                }

                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Failed to unload '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("'", NamedTextColor.RED))
                );

                return false;
            }

            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Unloaded '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("'", NamedTextColor.AQUA))
            );

            return true;
        });
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar unload <worldname> [save]", NamedTextColor.RED))
                        .append(Component.text("Setting save will override config", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(true)
                .executes(UnloadCommand::run)
                .then(Commands.argument("save", BoolArgumentType.bool())
                        .executes(UnloadCommand::runOverrided)));
    }
}
