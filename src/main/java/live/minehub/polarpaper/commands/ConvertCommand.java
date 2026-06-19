package live.minehub.polarpaper.commands;

import ca.spottedleaf.concurrentutil.util.Priority;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.world.BlockSelector;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.core.world.PolarWriter;
import live.minehub.polarpaper.nms.VersionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConvertCommand extends PolarCmd {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertCommand.class);

    public ConvertCommand() {
        super("convert", "Convert the current world to polar");
    }

    private int execute(CommandContext<CommandSourceStack> ctx) {
        boolean saveLight = false;
        try {
            saveLight = ctx.getArgument("save light", Boolean.class);
        } catch (IllegalArgumentException ignored) {}
        return execute(ctx, saveLight);
    }

    private int executeWithLight(CommandContext<CommandSourceStack> ctx) {
        return execute(ctx, ctx.getArgument("save light", Boolean.class));
    }

    private int execute(CommandContext<CommandSourceStack> ctx, boolean saveLight) {
        CommandSender sender = ctx.getSource().getSender();
        // Being ran from console
        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

        World bukkitWorld = player.getWorld();
        NamespacedKey worldKey = bukkitWorld.getKey();

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator != null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldKey.getKey(), NamedTextColor.RED))
                            .append(Component.text("' is already converted! ", NamedTextColor.RED))
                            .append(Component.text("Use ", NamedTextColor.RED))
                            .append(Component.text("/polar save ", NamedTextColor.WHITE))
                            .append(Component.text(worldKey.getKey(), NamedTextColor.WHITE))
            );
            return Command.SINGLE_SUCCESS;
        }

        String newWorldName = ctx.getArgument("new world name", String.class);
        Integer chunkRadius = ctx.getArgument("chunk radius", Integer.class);

        NamespacedKey newWorldKey = NamespacedKey.fromString(newWorldName, PolarPaper.getPlugin());
        if (newWorldKey == null) {
            sender.sendMessage(Component.text("Invalid world name", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        World newBukkitWorld = Bukkit.getWorld(newWorldKey);
        if (newBukkitWorld != null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(newBukkitWorld.getKey().toString(), NamedTextColor.RED))
                            .append(Component.text("' already exists!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        long before = System.nanoTime();

        sender.sendMessage(
                Component.text()
                        .append(Component.text("Loading chunks in '", NamedTextColor.GRAY))
                        .append(Component.text(worldKey.toString(), NamedTextColor.GRAY))
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
                // FULL required when saving light; FEATURES sufficient otherwise
                ChunkStatus status = saveLight ? ChunkStatus.FULL : ChunkStatus.FEATURES;
                level.moonrise$getChunkTaskScheduler().scheduleChunkLoad(x + offsetX, z + offsetZ, status, true, Priority.LOW, _ -> {
                    future.complete(null);
                });
                futures.add(future);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("Converting '", NamedTextColor.GRAY))
                            .append(Component.text(worldKey.toString(), NamedTextColor.GRAY))
                            .append(Component.text("'...", NamedTextColor.GRAY))
            );

            Config config = Polar.updateConfig(bukkitWorld, newWorldKey.getKey()).toBuilder().saveLight(saveLight).build();

            Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), _ -> {
                PolarWorld newPolarWorld = PolarWorld.convert(bukkitWorld, VersionUtil.getPolarFeaturesWorldAccess(), BlockSelector.square(offsetX, offsetZ, chunkRadius), config);
                byte[] polarBytes = PolarWriter.write(newPolarWorld);
                try {
                    Polar.getDefaultFolderSource(newWorldName).saveBytes(polarBytes);
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Failed to save '" + worldKey.getKey() + "'"));
                    LOGGER.error("Failed to save: " + worldKey.getKey(), e);
                    return;
                }

                int ms = (int) ((System.nanoTime() - before) / 1_000_000);
                sender.sendMessage(
                        Component.text()
                                .append(Component.text("Converted '", NamedTextColor.AQUA))
                                .append(Component.text(worldKey.toString(), NamedTextColor.AQUA))
                                .append(Component.text("' to '", NamedTextColor.AQUA))
                                .append(Component.text(newWorldKey.getKey(), NamedTextColor.AQUA))
                                .append(Component.text("' in ", NamedTextColor.AQUA))
                                .append(Component.text(ms, NamedTextColor.AQUA))
                                .append(Component.text("ms. ", NamedTextColor.AQUA))
                                .append(Component.text("Click to load now", NamedTextColor.WHITE, TextDecoration.UNDERLINED)
                                        .clickEvent(ClickEvent.runCommand("/polar load " + newWorldKey.getKey()))
                                        .hoverEvent(HoverEvent.showText(Component.text()
                                                .append(Component.text("Click to run ", NamedTextColor.AQUA))
                                                .append(Component.text("/polar load " + newWorldKey.getKey())))))
                );
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar convert <new world name> <chunk radius> [save light] (While in a non-polar world) to convert the chunks around you", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("new world name", StringArgumentType.string())
                .suggests((ctx, b) -> {
                    if (ctx.getSource().getSender() instanceof Player player) {
                        b.suggest(player.getWorld().getKey().value());
                    }
                    return b.buildFuture();
                })
                .then(Commands.argument("chunk radius", IntegerArgumentType.integer(1))
                        .executes(this::execute)
                        .then(Commands.argument("save light", BoolArgumentType.bool())
                                .executes(this::executeWithLight))
                )
        );
    }
}
