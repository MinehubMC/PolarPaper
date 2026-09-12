package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.userdata.WorldUserData;
import live.minehub.polarpaper.core.world.BlockSelector;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.core.world.PolarWriter;
import live.minehub.polarpaper.nms.VersionUtil;
import live.minehub.polarpaper.schematic.Schematic;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.resources.Identifier;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class CreateFromRegionCommand extends PolarCmd {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateFromRegionCommand.class);

    public CreateFromRegionCommand() {
        super("createfromregion", "Create a polar world from the selected region");
    }

    private static void createFromRegion(CommandContext<CommandSourceStack> ctx, World bukkitWorld, String newWorldName, Vector3i pos1, Vector3i pos2) {
        // newWorldName becomes a file name, so it must not be able to go outside the worlds folder
        if (WorldKey.validatePath(ctx.getSource().getSender(), newWorldName) == null) return;

        long before = System.nanoTime();

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Creating world '", NamedTextColor.GRAY))
                        .append(Component.text(newWorldName, NamedTextColor.GRAY))
                        .append(Component.text("' from selected region...", NamedTextColor.GRAY))
        );

        BlockSelector.RegionBlockSelector blockSelector = BlockSelector.RegionBlockSelector.fromCorners(pos1, pos2);

        Vector3i schemOffset = new Vector3i();
        // Being ran from console
        if (ctx.getSource().getSender() instanceof Player player) {
            schemOffset = player.getLocation().toVector().toVector3i();
        }
        Vector3i finalSchemOffset = schemOffset;

        // TODO: option to center the world

        CompletableFuture<PolarWorld> future = PolarWorld.convert(bukkitWorld, VersionUtil.getPolarFeaturesWorldAccess(), blockSelector, true);
        future.thenAcceptAsync(polarWorld -> {
            polarWorld.userData(WorldUserData.writeSchematicOffset(finalSchemOffset));
            PolarSource source = Polar.getDefaultFolderSource(newWorldName);
            try {
                PolarWriter.write(source, polarWorld);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((_, e) -> {
            if (e != null) {
                String errorMsg = String.format("Failed to create '%s', please check logs for error", newWorldName);
                LOGGER.error(errorMsg, e);
                ctx.getSource().getSender().sendMessage(Component.text(errorMsg, NamedTextColor.RED));
                return;
            }

            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Converted '", NamedTextColor.AQUA))
                            .append(Component.text(newWorldName, NamedTextColor.AQUA))
                            .append(Component.text("' in ", NamedTextColor.AQUA))
                            .append(Component.text(ms, NamedTextColor.AQUA))
                            .append(Component.text("ms. ", NamedTextColor.AQUA))
                            .append(Component.text("\nUse ", NamedTextColor.AQUA))
                            .append(
                                    Component.text()
                                            .append(Component.text("/polar paste ", NamedTextColor.WHITE))
                                            .append(Component.text(newWorldName, NamedTextColor.WHITE))
                                            .clickEvent(ClickEvent.runCommand("/polar paste " + newWorldName))
                                            .hoverEvent(HoverEvent.showText(Component.text("Click to run")))
                                            .decorate(TextDecoration.UNDERLINED))
                            .append(Component.text(" to paste it now", NamedTextColor.AQUA))
                            .append(Component.text("\nUse ", NamedTextColor.AQUA))
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
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // Being ran from console
        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

        World bukkitWorld = player.getWorld();

        String newWorldName = ctx.getArgument("new world name", String.class);

        PersistentDataContainer data = player.getPersistentDataContainer();
        int[] pos1Array = data.get(Schematic.POS_1_KEY, PersistentDataType.INTEGER_ARRAY);
        int[] pos2Array = data.get(Schematic.POS_2_KEY, PersistentDataType.INTEGER_ARRAY);
        if (pos1Array == null || pos2Array == null) {
            ctx.getSource().getSender().sendMessage(Component.text("You need to select two corners with the polar wand!", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        Vector3i pos1 = new Vector3i(pos1Array);
        Vector3i pos2 = new Vector3i(pos2Array);

        createFromRegion(ctx, bukkitWorld, newWorldName, pos1, pos2);
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar createfromregion <new worldname> (While in a world) to create a new polar world from the selected region", NamedTextColor.RED))
                        .appendNewline()
                        .append(Component.text("or: /polar createfromregion <new worldname> <world name> <pos1> <pos2> to create a new polar world from the specified region", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("new world name", StringArgumentType.string())
                .executes(CreateFromRegionCommand::run)
                .then(createWorldNameArgument(false)
                        .then(Commands.argument("x1", IntegerArgumentType.integer())
                                .then(Commands.argument("y1", IntegerArgumentType.integer())
                                        .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                        .then(Commands.argument("y2", IntegerArgumentType.integer())
                                                                .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                        .executes(ctx -> {
                                                                            Identifier worldId = ctx.getArgument("world name", Identifier.class);
                                                                            String newWorldName = ctx.getArgument("new world name", String.class);

                                                                            Vector3i pos1 = new Vector3i(
                                                                                    ctx.getArgument("x1", Integer.class),
                                                                                    ctx.getArgument("y1", Integer.class),
                                                                                    ctx.getArgument("z1", Integer.class)
                                                                            );
                                                                            Vector3i pos2 = new Vector3i(
                                                                                    ctx.getArgument("x2", Integer.class),
                                                                                    ctx.getArgument("y2", Integer.class),
                                                                                    ctx.getArgument("z2", Integer.class)
                                                                            );

                                                                            World bukkitWorld = WorldKey.getWorld(worldId);

                                                                            createFromRegion(ctx, bukkitWorld, newWorldName, pos1, pos2);
                                                                            return Command.SINGLE_SUCCESS;
                                                                        })))))))));
    }
}
