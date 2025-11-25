package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class HelpCommand {

    private static final Component MESSAGE = Component.text()
            .append(Component.text("Polar commands:\n", NamedTextColor.AQUA))
            .append(Component.text("- /polar convert <new worldname> <chunk radius>\n", NamedTextColor.AQUA))
            .append(Component.text("  Convert the current world to polar\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar createblank <new worldname>\n", NamedTextColor.AQUA))
            .append(Component.text("  Create a blank world\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar createfromregion <new worldname>\n", NamedTextColor.AQUA))
            .append(Component.text("  Create a polar world from the selected region\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar goto <worldname>\n", NamedTextColor.AQUA))
            .append(Component.text("  Teleport to a world\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar info [worldname] (or while in a polar world)\n", NamedTextColor.AQUA))
            .append(Component.text("  Get info for a polar world\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar list\n", NamedTextColor.AQUA))
            .append(Component.text("  List all polar worlds\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar load <worldname>\n", NamedTextColor.AQUA))
            .append(Component.text("  Load a polar world from the worlds folder\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar unload <worldname>\n", NamedTextColor.AQUA))
            .append(Component.text("  Unload a polar world\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar paste <worldname> [rotation] [air ignore]\n", NamedTextColor.AQUA))
            .append(Component.text("  Place a polar world like a schematic\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar save <worldname>\n", NamedTextColor.AQUA))
            .append(Component.text("  Save the polar world\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar setspawn <worldname> [rounded]\n", NamedTextColor.AQUA))
            .append(Component.text("  Set the spawn of this polar world\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar wand\n", NamedTextColor.AQUA))
            .append(Component.text("  Give a wand to select corners\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar pos1, /polar pos2\n", NamedTextColor.AQUA))
            .append(Component.text("  Set the second corner position\n", NamedTextColor.GRAY))
            .append(Component.text("- /polar reloadconfig\n", NamedTextColor.AQUA))
            .append(Component.text("  Reload the config\n", NamedTextColor.GRAY))
            .build();

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(MESSAGE);
        return Command.SINGLE_SUCCESS;
    }

}
