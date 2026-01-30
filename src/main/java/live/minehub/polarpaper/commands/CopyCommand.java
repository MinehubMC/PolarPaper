package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.util.ExceptionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.nio.file.Files;
import java.nio.file.Path;

public class CopyCommand {

    public static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        String worldName = ctx.getArgument("worldname", String.class);
        String newWorldName = ctx.getArgument("newworldname", String.class);

        Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path path = worldsFolder.resolve(worldName + ".polar");

        if (!Files.exists(path)) {
            sender.sendMessage(Component.text("Couldn't find file '" + worldName + ".polar' in the worlds folder", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        byte[] polarBytes;
        try {
            polarBytes = Files.readAllBytes(path);
        } catch (Exception e) {
            PolarPaper.logger().warning("Failed to load world '" + worldName + ".polar'");
            sender.sendMessage(Component.text("Failed to load world '" + worldName + ".polar'", NamedTextColor.RED));
            ExceptionUtil.log(e);
            return Command.SINGLE_SUCCESS;
        }

        Polar.createWorld(polarBytes, newWorldName);

        sender.sendMessage(
                Component.text()
                        .append(Component.text("Copied '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("' to '", NamedTextColor.AQUA))
                        .append(Component.text(newWorldName, NamedTextColor.AQUA))
                        .append(Component.text("'. ", NamedTextColor.AQUA))
                        .append(Component.text("Use ", NamedTextColor.AQUA))
                        .append(
                                Component.text()
                                        .append(Component.text("/polar goto ", NamedTextColor.WHITE))
                                        .append(Component.text(newWorldName, NamedTextColor.WHITE))
                                        .clickEvent(ClickEvent.runCommand("/polar goto " + newWorldName))
                                        .hoverEvent(HoverEvent.showText(Component.text("Click to run")))
                                        .decorate(TextDecoration.UNDERLINED))
                        .append(Component.text(" to teleport now", NamedTextColor.AQUA))
        );

        return Command.SINGLE_SUCCESS;
    }

}
