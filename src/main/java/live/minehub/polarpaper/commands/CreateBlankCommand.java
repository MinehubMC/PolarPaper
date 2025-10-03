package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

        Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");
        try {
            Files.write(worldsFolder.resolve(worldName + ".polar"), polarBytes);
        } catch (IOException e) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Failed to create world '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
            );
            PolarPaper.logger().warning("Error while creating blank world " + worldName);
            PolarPaper.logger().warning(e.toString());
            return Command.SINGLE_SUCCESS;
        }

        Config.writeToConfig(PolarPaper.getPlugin().getConfig(), worldName, Config.DEFAULT);

        Polar.loadWorld(new PolarWorld(), worldName, Config.DEFAULT);
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Created blank world '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'", NamedTextColor.AQUA))
        );


        return Command.SINGLE_SUCCESS;
    }

}
