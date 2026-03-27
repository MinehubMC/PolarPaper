package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarReader;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.PolarWriter;
import live.minehub.polarpaper.source.FilePolarSource;
import live.minehub.polarpaper.userdata.WorldUserData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.joml.Vector3i;

public class SetCenterCommand extends PolarCmd {

    public SetCenterCommand() {
        super("setcenter", "Set the schematic center of the current polar world");
    }

    protected static int convert(CommandContext<CommandSourceStack> ctx, Vector3i center) {
        CommandSender sender = ctx.getSource().getSender();
        // Being ran from console
        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

        World bukkitWorld = player.getWorld();
        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(bukkitWorld.getName(), NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {
            try {
                FilePolarSource source = FilePolarSource.defaultFolder(bukkitWorld.getName());
                PolarWorld newPolarWorld = PolarReader.read(source.readBytes());
                newPolarWorld.userData(WorldUserData.writeSchematicOffset(center));
                byte[] worldBytes = PolarWriter.write(newPolarWorld);
                source.saveBytes(worldBytes);
            } catch (Exception e) {
                String errorMsg = String.format("Failed to save '%s', please check logs for error", bukkitWorld.getName());
                PolarPaper.logger().severe(errorMsg);
                sender.sendMessage(Component.text(errorMsg, NamedTextColor.RED));
                return;
            }

            String formattedPos = String.format("%s, %s, %s", center.x(), center.y(), center.z());
            sender.sendMessage(Component.text("Set schematic center position to " + formattedPos, NamedTextColor.AQUA));
        });

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // Being ran from console
        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;
        Vector3i center = player.getLocation().toVector().toVector3i();
        return convert(ctx, center);
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    return convert(ctx, new Vector3i(
                                            ctx.getArgument("x", Integer.class),
                                            ctx.getArgument("y", Integer.class),
                                            ctx.getArgument("z", Integer.class)
                                    ));
                                }))));
    }
}
