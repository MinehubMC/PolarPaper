package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import live.minehub.polarpaper.PolarWorld;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

@SuppressWarnings("UnstableApiUsage")
public class ListCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        TextComponent.Builder builder = Component.text();

        builder.append(Component.text("List of worlds:", NamedTextColor.AQUA));

        for (World world : Bukkit.getServer().getWorlds()) {
            PolarWorld pw = PolarWorld.fromWorld(world);
            if (pw == null) continue;

            builder.append(Component.newline());
            builder.append(Component.text(" - ", NamedTextColor.AQUA));
            builder.append(Component.text(world.getName(), NamedTextColor.AQUA));
        }

        ctx.getSource().getSender().sendMessage(builder);

        return Command.SINGLE_SUCCESS;
    }

}
