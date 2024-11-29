package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import live.minehub.polarpaper.PaperPolar;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.PolarWriter;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("UnstableApiUsage")
public class CreateBlankCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("worldname", String.class);

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld != null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' already exists!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        byte[] polarBytes = PolarWriter.write(new PolarWorld());

        Path pluginFolder = Path.of(PaperPolar.getPlugin().getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");
        try {
            Files.write(worldsFolder.resolve(worldName + ".polar"), polarBytes);
        } catch (IOException e) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Failed to create world '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
            );
            PaperPolar.getPlugin().getLogger().warning("Error while creating blank world " + worldName);
            PaperPolar.getPlugin().getLogger().warning(e.toString());
            return Command.SINGLE_SUCCESS;
        }

        boolean successful = Polar.loadWorldConfigSource(worldName);
        if (!successful) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Failed to load world '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
            );
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Created blank world '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'", NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

}
