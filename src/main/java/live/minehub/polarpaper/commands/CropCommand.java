package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.*;
import live.minehub.polarpaper.schematic.Schematic;
import live.minehub.polarpaper.source.FilePolarSource;
import live.minehub.polarpaper.userdata.WorldUserData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3i;

public class CropCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar crop (while in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

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
        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator == null) return Command.SINGLE_SUCCESS;

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

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Cropping '", NamedTextColor.GRAY))
                        .append(Component.text(bukkitWorld.getName(), NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );

        long before = System.nanoTime();

        Polar.updateConfig(bukkitWorld, bukkitWorld.getName()); // config should only be updated synchronously

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {
            PolarWorld newPolarWorld = polarWorld.updateChunks(bukkitWorld, polarGenerator.getWorldAccess(), blockSelector);
            newPolarWorld.userData(WorldUserData.writeSchematicOffset(schemOffset));
            byte[] worldBytes = PolarWriter.write(newPolarWorld);
            FilePolarSource.defaultFolder(bukkitWorld.getName()).saveBytes(worldBytes);

            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Cropped '", NamedTextColor.AQUA))
                            .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
                            .append(Component.text("' in ", NamedTextColor.AQUA))
                            .append(Component.text(ms, NamedTextColor.AQUA))
                            .append(Component.text("ms", NamedTextColor.AQUA))
            );
        });

        return Command.SINGLE_SUCCESS;
    }

}
