package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.util.TaskFutures;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class SaveCommand extends PolarCmd {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaveCommand.class);

    public SaveCommand() {
        super("save", "Save the polar world");
    }

    protected static CompletableFuture<Boolean> saveWorld(CommandContext<CommandSourceStack> ctx, String worldName) {
        CommandSender sender = ctx.getSource().getSender();

        NamespacedKey worldKey = NamespacedKey.fromString(worldName, PolarPaper.getPlugin());
        World bukkitWorld = worldKey == null ? null : Bukkit.getWorld(worldKey);
        if (bukkitWorld == null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist!", NamedTextColor.RED))
            );
            return CompletableFuture.completedFuture(false);
        }

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator == null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return CompletableFuture.completedFuture(false);
        }

        sender.sendMessage(
                Component.text()
                        .append(Component.text("Saving '", NamedTextColor.GRAY))
                        .append(Component.text(worldName, NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );

        long before = System.nanoTime();

        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            Polar.updateConfig(bukkitWorld, bukkitWorld.getKey().getKey()); // config should only be updated synchronously
        });

        return TaskFutures.runAsync(PolarPaper.getPlugin(), () -> {
            Polar.saveWorld(bukkitWorld);
            return null;
        }).handle((_, ex) -> {
            if (ex != null) {
                String errorMsg = String.format("Failed to save '%s'", bukkitWorld.getKey().getKey());
                LOGGER.error(errorMsg, ex);
                sender.sendMessage(Component.text(errorMsg, NamedTextColor.RED));
                return false;
            }

            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("Saved '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("' in ", NamedTextColor.AQUA))
                            .append(Component.text(ms, NamedTextColor.AQUA))
                            .append(Component.text("ms", NamedTextColor.AQUA))
            );

            return true;
        });
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world name", String.class);
        saveWorld(ctx, worldName);
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar save <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(true, true)
                .executes(SaveCommand::run));
    }
}
