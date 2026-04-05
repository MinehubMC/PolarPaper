package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.*;
import live.minehub.polarpaper.schematic.Schematic;
import live.minehub.polarpaper.source.FilePolarSource;
import live.minehub.polarpaper.userdata.WorldUserData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3i;

public class CreateFromRegionCommand extends PolarCmd {

    public CreateFromRegionCommand() {
        super("createfromregion", "Create a polar world from the selected region");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // Being ran from console
        if (!(sender instanceof Player player)) return Command.SINGLE_SUCCESS;

        World bukkitWorld = player.getWorld();

        String newWorldName = ctx.getArgument("newworldname", String.class);

        long before = System.nanoTime();

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Creating world '", NamedTextColor.GRAY))
                        .append(Component.text(newWorldName, NamedTextColor.GRAY))
                        .append(Component.text("' from selected region...", NamedTextColor.GRAY))
        );

        PersistentDataContainer data = player.getPersistentDataContainer();
        int[] pos1Array = data.get(Schematic.POS_1_KEY, PersistentDataType.INTEGER_ARRAY);
        int[] pos2Array = data.get(Schematic.POS_2_KEY, PersistentDataType.INTEGER_ARRAY);
        if (pos1Array == null || pos2Array == null) {
            ctx.getSource().getSender().sendMessage(Component.text("You need to select two corners with the polar wand!", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        Vector3i pos1 = new Vector3i(pos1Array);
        Vector3i pos2 = new Vector3i(pos2Array);
        BlockSelector blockSelector = BlockSelector.RegionBlockSelector.fromCorners(pos1, pos2);

        Vector3i schemOffset = player.getLocation().toVector().toVector3i();

        // TODO: center the world

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {
            try {
                PolarWorld polarWorld = PolarWorld.convert(bukkitWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, blockSelector);
                polarWorld.userData(WorldUserData.writeSchematicOffset(schemOffset));
                byte[] worldBytes = PolarWriter.write(polarWorld);
                FilePolarSource.defaultFolder(newWorldName).saveBytes(worldBytes);
            } catch (Exception e) {
                String errorMsg = String.format("Failed to create '%s', please check logs for error", newWorldName);
                PolarPaper.logger().severe(errorMsg);
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

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar createfromregion <new worldname> (While in a world) to create a new polar world from the selected region", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("newworldname", StringArgumentType.string())
                .executes(CreateFromRegionCommand::run));
    }
}
