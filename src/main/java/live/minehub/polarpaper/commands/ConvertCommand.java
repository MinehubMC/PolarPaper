package live.minehub.polarpaper.commands;

import ca.spottedleaf.concurrentutil.util.Priority;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.*;
import live.minehub.polarpaper.source.FilePolarSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConvertCommand extends PolarCmd {

    public ConvertCommand() {
        super("convert", "Convert the current world to polar");
    }

    private int execute(CommandContext<CommandSourceStack> ctx) {
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
                        .append(Component.text("Loading chunks in '", NamedTextColor.GRAY))
                        .append(Component.text(newWorldName, NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );

        Chunk playerChunk = player.getChunk();
        int offsetX = playerChunk.getX();
        int offsetZ = playerChunk.getZ();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ServerLevel level = ((CraftWorld)bukkitWorld).getHandle();
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                // FEATURES status as we do not need light
                // should be changed to FULL if/when light saving is added
                level.moonrise$getChunkTaskScheduler().scheduleChunkLoad(x + offsetX, z + offsetZ, ChunkStatus.FEATURES, true, Priority.LOW, (realChunk) -> {
                    future.complete(null);
                });
                futures.add(future);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Converting '", NamedTextColor.GRAY))
                            .append(Component.text(newWorldName, NamedTextColor.GRAY))
                            .append(Component.text("'...", NamedTextColor.GRAY))
            );

            Config config = Polar.updateConfig(bukkitWorld, newWorldName);

            Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {
                PolarWorld newPolarWorld = PolarWorld.convert(bukkitWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, BlockSelector.square(offsetX, offsetZ, chunkRadius), config);
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
        });

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar convert <new worldname> <chunk radius> (While in a non-polar world) to convert the chunks around you", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("newworldname", StringArgumentType.string())
                .then(Commands.argument("chunkradius", IntegerArgumentType.integer(1))
                        .executes(this::execute)));
    }
}
