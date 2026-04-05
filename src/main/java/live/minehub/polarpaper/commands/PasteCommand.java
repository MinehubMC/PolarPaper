package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarReader;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.schematic.Rotation;
import live.minehub.polarpaper.schematic.Schematic;
import live.minehub.polarpaper.schematic.Setter;
import live.minehub.polarpaper.util.ExceptionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.joml.Vector3i;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PasteCommand extends PolarCmd {

    public PasteCommand() {
        super("paste", "Place a polar world like a schematic");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar paste <world> [rotation] (while in a world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        return paste(ctx, Rotation.NONE, Schematic.IgnoreAir.ALL);
    }

    protected static int runWithRotation(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar paste <world> [rotation] (while in a world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        String rotationString = ctx.getArgument("rotation", String.class);

        Rotation rotation = Rotation.fromFriendlyName(rotationString.toLowerCase());
        if (rotation == null) {
            ctx.getSource().getSender().sendMessage(Component.text("Invalid rotation '" + rotationString + "'", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        return paste(ctx, rotation, Schematic.IgnoreAir.ALL);
    }

    protected static int runWithRotationAndAirIgnore(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar paste <world> [rotation] [air ignore] (while in a world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        String rotationString = ctx.getArgument("rotation", String.class);

        Rotation rotation = Rotation.fromFriendlyName(rotationString.toLowerCase());
        if (rotation == null) {
            ctx.getSource().getSender().sendMessage(Component.text("Invalid rotation '" + rotationString + "'", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String ignoreAirString = ctx.getArgument("ignoreair", String.class);

        try {
            Schematic.IgnoreAir ignoreAir = Schematic.IgnoreAir.valueOf(ignoreAirString.toUpperCase());
            return paste(ctx, rotation, ignoreAir);
        } catch (IllegalArgumentException ignored) {
            ctx.getSource().getSender().sendMessage(Component.text("Invalid air ignore '" + ignoreAirString + "'", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
    }

    private static int paste(CommandContext<CommandSourceStack> ctx, Rotation rotation, Schematic.IgnoreAir ignoreAir) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;

        long before = System.nanoTime();

        String worldName = ctx.getArgument("worldname", String.class);

        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path path = worldsFolder.resolve(worldName + ".polar");

        if (!Files.exists(path)) {
            player.sendMessage(Component.text("Couldn't find file '" + worldName + ".polar' in the worlds folder", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        PolarWorld polarWorld;
        try {
            byte[] polarBytes;
            try {
                polarBytes = Files.readAllBytes(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            polarWorld = PolarReader.read(polarBytes);
        } catch (Exception e) {
            PolarPaper.logger().warning("Failed to load world '" + worldName + ".polar'");
            player.sendMessage(Component.text("Failed to load world '" + worldName + ".polar'", NamedTextColor.RED));
            ExceptionUtil.log(e);
            return Command.SINGLE_SUCCESS;
        }

        Vector3i pasteOffset = player.getLocation().toVector().toVector3i();

        try {
            Setter setter = new Setter.World(player.getWorld());
            Schematic.paste(polarWorld, setter, pasteOffset, rotation, ignoreAir);
        } catch (Exception e) {
            String errorMsg = "Failed to paste schematic, please check logs for error";
            PolarPaper.logger().severe(errorMsg);
            ctx.getSource().getSender().sendMessage(Component.text(errorMsg, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        int ms = (int) ((System.nanoTime() - before) / 1_000_000);
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Pasted '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("' in ", NamedTextColor.AQUA))
                        .append(Component.text(ms, NamedTextColor.AQUA))
                        .append(Component.text("ms", NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar paste <worldname> [rotation] [air ignore] (While in a world) to place a polar world at your current position", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("worldname", StringArgumentType.string())
                .executes(PasteCommand::run)
                .then(Commands.argument("rotation", StringArgumentType.string())
                        .suggests((ctx, s) -> {
                            for (Rotation rotation : Rotation.values()) {
                                s.suggest(rotation.getFriendlyName());
                            }
                            return s.buildFuture();
                        })
                        .executes(PasteCommand::runWithRotation)
                        .then(Commands.argument("ignoreair", StringArgumentType.string())
                                .suggests((ctx, s) -> {
                                    for (Schematic.IgnoreAir ignoreAir : Schematic.IgnoreAir.values()) {
                                        s.suggest(ignoreAir.name().toLowerCase());
                                    }
                                    return s.buildFuture();
                                })
                                .executes(PasteCommand::runWithRotationAndAirIgnore))));
    }
}
