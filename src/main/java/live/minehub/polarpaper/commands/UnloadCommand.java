package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarGenerator;
import live.minehub.polarpaper.PolarWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

@SuppressWarnings("UnstableApiUsage")
public class UnloadCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        return unload(ctx, false, false);
    }

    protected static int runOverrided(CommandContext<CommandSourceStack> ctx) {
        Boolean override = ctx.getArgument("save", Boolean.class);
        return unload(ctx, true, override);
    }

    protected static int unload(CommandContext<CommandSourceStack> ctx, boolean saveOverrided, boolean save) {
        String worldName = ctx.getArgument("worldname", String.class);

        World bukkitWorld = Bukkit.getWorld(worldName);
        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' already not loaded!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarGenerator generator = PolarGenerator.fromWorld(bukkitWorld);
        if (generator == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }
        Config config = generator.getConfig();

        boolean shouldSave;
        if (saveOverrided) {
            shouldSave = save;
        } else {
            shouldSave = config.autoSave();
        }

        if (shouldSave) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Saving '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("'...", NamedTextColor.AQUA))
            );

            Polar.saveWorldConfigSource(bukkitWorld, polarWorld, generator, () -> {
                bukkitUnload(ctx, bukkitWorld);
            }, () -> {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Something went wrong while trying to save '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("', world will not be unloaded", NamedTextColor.RED))
                );
            });
        } else {
            if (saveOverrided) {
                ctx.getSource().getSender().sendMessage(Component.text("Save force disabled, world will not be saved before unload", NamedTextColor.AQUA));
            } else {
                ctx.getSource().getSender().sendMessage(Component.text("Autosave is disabled, world will not be saved before unload", NamedTextColor.AQUA));
            }

            bukkitUnload(ctx, bukkitWorld);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static void bukkitUnload(CommandContext<CommandSourceStack> ctx, World bukkitWorld) {
        String worldName = bukkitWorld.getName();
        boolean successful = Bukkit.unloadWorld(bukkitWorld, false);

        if (successful) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Unloaded '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("'", NamedTextColor.AQUA))
            );
        } else {
            if (!bukkitWorld.getPlayers().isEmpty()) {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("There are still players in '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("'", NamedTextColor.RED))
                );
            } else {
                ctx.getSource().getSender().sendMessage(
                        Component.text()
                                .append(Component.text("Something went wrong while unloading '", NamedTextColor.RED))
                                .append(Component.text(worldName, NamedTextColor.RED))
                                .append(Component.text("'", NamedTextColor.RED))
                );
            }
        }
    }

}
