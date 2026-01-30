package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class ListCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        TextComponent.Builder builder = Component.text();

        builder.append(Component.text("List of worlds:", NamedTextColor.AQUA));

        for (World world : Bukkit.getServer().getWorlds()) {
            if (world == null) continue;
            PolarGenerator pw = PolarGenerator.fromWorld(world);
            if (pw == null) continue;

            builder.append(Component.newline());
            builder.append(Component.text(" - ", NamedTextColor.WHITE));
            builder.append(Component.text(world.getName(), NamedTextColor.WHITE));

            if (ctx.getSource().getSender().hasPermission("polarpaper.goto")) {
                builder.appendSpace();
                builder.append(Component.text("[GOTO]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar goto " + world.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to run"))));
            }
        }

        ctx.getSource().getSender().sendMessage(builder);

        return Command.SINGLE_SUCCESS;
    }

}
