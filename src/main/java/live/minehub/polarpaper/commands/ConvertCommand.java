package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.*;
import live.minehub.polarpaper.source.FilePolarSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ConvertCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        return convert(ctx);
    }

    private static int convert(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // Being ran from console
        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

        World bukkitWorld = player.getWorld();
        String worldName = bukkitWorld.getName();

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld != null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is already converted! ", NamedTextColor.RED))
                            .append(Component.text("Use ", NamedTextColor.RED))
                            .append(Component.text("/polar save ", NamedTextColor.WHITE))
                            .append(Component.text(worldName, NamedTextColor.WHITE))
            );
            return Command.SINGLE_SUCCESS;
        }

        String newWorldName = ctx.getArgument("newworldname", String.class);
        Integer chunkRadius = ctx.getArgument("chunkradius", Integer.class);

        World newBukkitWorld = Bukkit.getWorld(newWorldName);
        if (newBukkitWorld != null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(newBukkitWorld.getName(), NamedTextColor.RED))
                            .append(Component.text("' already exists!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        long before = System.nanoTime();

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Converting '", NamedTextColor.GRAY))
                        .append(Component.text(newWorldName, NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );

        Polar.updateConfig(bukkitWorld, newWorldName);

        Chunk playerChunk = player.getChunk();
        int offsetX = playerChunk.getX();
        int offsetZ = playerChunk.getZ();

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {
            PolarWorld newPolarWorld = PolarWorld.fromWorld(bukkitWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, BlockSelector.square(offsetX, offsetZ, chunkRadius));
            byte[] polarBytes = PolarWriter.write(newPolarWorld);
            FilePolarSource.defaultFolder(newWorldName).saveBytes(polarBytes);

            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Converted '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("' in ", NamedTextColor.AQUA))
                            .append(Component.text(ms, NamedTextColor.AQUA))
                            .append(Component.text("ms. ", NamedTextColor.AQUA))
                            .append(Component.text("Use ", NamedTextColor.AQUA))
                            .append(
                                    Component.text()
                                            .append(Component.text("/polar load ", NamedTextColor.WHITE))
                                            .append(Component.text(newWorldName, NamedTextColor.WHITE))
                                            .clickEvent(ClickEvent.runCommand("/polar load " + newWorldName))
                                            .hoverEvent(HoverEvent.showText(Component.text("Click to run")))
                                            .decorate(TextDecoration.UNDERLINED))
                            .append(Component.text(" to load it now", NamedTextColor.AQUA))
            );
        });

        return Command.SINGLE_SUCCESS;
    }

}
