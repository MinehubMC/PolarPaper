package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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

public class CreateFromRegionCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        return convert(ctx);
    }

    private static int convert(CommandContext<CommandSourceStack> ctx) {
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

        PolarWorld polarWorld = new PolarWorld((byte)-4, (byte)19);

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
            polarWorld.updateChunks(bukkitWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, blockSelector, false);
            polarWorld.userData(WorldUserData.writeSchematicOffset(schemOffset));
            byte[] worldBytes = PolarWriter.write(polarWorld);
            FilePolarSource.defaultFolder(newWorldName).saveBytes(worldBytes);

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

}
