package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@SuppressWarnings("UnstableApiUsage")
public class HelpCommand {

    private static final Component MESSAGE = Component.text()
            .append(Component.text("Polar commands:\n", NamedTextColor.AQUA))
            .append(Component.text(" /polar convert <chunk radius> [centered] (While in a non-polar world) - Convert a non-polar world\n", NamedTextColor.AQUA))
            .append(Component.text(" /polar createblank <worldname> - Create a blank world\n", NamedTextColor.AQUA))
            .append(Component.text(" /polar goto <worldname> - Teleport to a world\n", NamedTextColor.AQUA))
            .append(Component.text(" /polar info (while in a polar world) - Get info for your world\n", NamedTextColor.AQUA))
            .append(Component.text(" /polar load <worldname> - Load a world from config source\n", NamedTextColor.AQUA))
            .append(Component.text(" /polar save <worldname> - Save a world to config source\n", NamedTextColor.AQUA))
            .append(Component.text(" /polar setspawn <worldname> [rounded] - Set the spawn", NamedTextColor.AQUA))
            .build();

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(MESSAGE);
        return Command.SINGLE_SUCCESS;
    }

}
