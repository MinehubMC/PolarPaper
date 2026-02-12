package live.minehub.polarpaper.commands;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.userdata.WorldUserData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.joml.Vector3i;

import java.util.List;

public class InfoCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar info (while in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        return printInfo(ctx, player.getWorld().getName());
    }

    protected static int runArg(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("worldname", String.class);
        return printInfo(ctx, worldName);
    }

    protected static int printInfo(CommandContext<CommandSourceStack> ctx, String worldName) {
        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            Config config = Config.readFromConfig(PolarPaper.getPlugin().getConfig(), bukkitWorld);

            byte[] userData = polarWorld.userData();
            Vector3i offset = WorldUserData.readSchematicOffset(userData);

            List<NewChunkHolder> chunkHolders = ((ChunkSystemServerLevel) ((CraftWorld) bukkitWorld).getHandle()).moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolders();

            TextComponent.Builder builder = Component.text()
                    .append(Component.text("Info for ", NamedTextColor.AQUA))
                    .append(Component.text(bukkitWorld.getName(), NamedTextColor.AQUA))
                    .append(Component.text(":", NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text(" Version: ", NamedTextColor.AQUA))
                    .append(Component.text(polarWorld.version(), NamedTextColor.AQUA))
                    .append(Component.text(" (", NamedTextColor.AQUA))
                    .append(Component.text(polarWorld.dataVersion(), NamedTextColor.AQUA))
                    .append(Component.text(")", NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text(" Compression: ", NamedTextColor.AQUA))
                    .append(Component.text(polarWorld.compression().name(), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text(" Spawn: ", NamedTextColor.AQUA))
                    .append(Component.text(config.spawnString(), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text(" Held Polar Chunks: ", NamedTextColor.AQUA))
                    .append(Component.text(polarWorld.chunks().size(), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text(" Chunk Holders: ", NamedTextColor.AQUA))
                    .append(Component.text(chunkHolders.size(), NamedTextColor.AQUA));

            if (offset != null) {
                builder.appendNewline();
                builder.append(Component.text(" Schematic center: ", NamedTextColor.AQUA));
                builder.append(Component.text(offset.x + ", " + offset.y + ", " + offset.z, NamedTextColor.AQUA));
            }

            ctx.getSource().getSender().sendMessage(builder.build());
        });

        return Command.SINGLE_SUCCESS;
    }

}
