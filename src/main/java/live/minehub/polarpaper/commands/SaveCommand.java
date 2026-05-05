package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.generator.PolarGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class SaveCommand extends PolarCmd {

    public SaveCommand() {
        super("save", "Save the polar world");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world name", String.class);

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

        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            Polar.updateConfig(bukkitWorld, bukkitWorld.getName()); // config should only be updated synchronously
        });

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), _ -> {
            try {
                Polar.saveWorldToFile(bukkitWorld);
            } catch (Exception e) {
                String errorMsg = String.format("Failed to save '%s', please check logs for error", bukkitWorld.getName());
                PolarPaper.logger().severe(errorMsg);
                ctx.getSource().getSender().sendMessage(Component.text(errorMsg, NamedTextColor.RED));
                return;
            }

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

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar save <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(true, true)
                .executes(SaveCommand::run));
    }
}
