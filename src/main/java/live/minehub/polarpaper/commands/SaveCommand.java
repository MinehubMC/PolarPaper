package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SaveCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        return save(ctx, ChunkSelector.all());
    }
    protected static int runSelected(CommandContext<CommandSourceStack> ctx) {
        Integer chunkRadius = ctx.getArgument("chunkradius", Integer.class);

        return save(ctx, ChunkSelector.square(0, 0, chunkRadius));
    }
    protected static int runSelectedCentered(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run this command as a player to center the radius around you, otherwise run without centered to center it around 0,0");
            return Command.SINGLE_SUCCESS;
        }
        Integer chunkRadius = ctx.getArgument("chunkradius", Integer.class);

        Chunk playerChunk = player.getChunk();
        int offsetX = playerChunk.getX();
        int offsetZ = playerChunk.getZ();

        return save(ctx, ChunkSelector.square(offsetX, offsetZ, chunkRadius));
    }

    protected static int save(CommandContext<CommandSourceStack> ctx, ChunkSelector chunkSelector) {
        String worldName = ctx.getArgument("worldname", String.class);

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Saving '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'...", NamedTextColor.AQUA))
        );

        long before = System.nanoTime();

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {
            Polar.saveWorldConfigSource(bukkitWorld, polarWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, chunkSelector, 0, 0).thenAccept(successful -> {
                if (successful) {
                    int ms = (int) ((System.nanoTime() - before) / 1_000_000);
                    ctx.getSource().getSender().sendMessage(
                            Component.text()
                                    .append(Component.text("Saved '", NamedTextColor.AQUA))
                                    .append(Component.text(worldName, NamedTextColor.AQUA))
                                    .append(Component.text("' in ", NamedTextColor.AQUA))
                                    .append(Component.text(ms, NamedTextColor.AQUA))
                                    .append(Component.text("ms", NamedTextColor.AQUA))
                    );
                } else {
                    ctx.getSource().getSender().sendMessage(
                            Component.text()
                                    .append(Component.text("Something went wrong while trying to save '", NamedTextColor.RED))
                                    .append(Component.text(worldName, NamedTextColor.RED))
                                    .append(Component.text("'", NamedTextColor.RED))
                    );
                }
            });
        });

        return Command.SINGLE_SUCCESS;
    }

}
