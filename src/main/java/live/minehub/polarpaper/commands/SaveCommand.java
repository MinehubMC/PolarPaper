package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarGenerator;
import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class SaveCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("worldname", String.class);

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Saving '", NamedTextColor.GRAY))
                        .append(Component.text(worldName, NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );

        long before = System.nanoTime();

        Polar.updateConfig(bukkitWorld, bukkitWorld.getName()); // config should only be updated synchronously

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {
            Polar.saveWorldToFile(bukkitWorld);

            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Saved '", NamedTextColor.AQUA))
                            .append(Component.text(worldName, NamedTextColor.AQUA))
                            .append(Component.text("' in ", NamedTextColor.AQUA))
                            .append(Component.text(ms, NamedTextColor.AQUA))
                            .append(Component.text("ms", NamedTextColor.AQUA))
            );
        });

        return Command.SINGLE_SUCCESS;
    }

}
