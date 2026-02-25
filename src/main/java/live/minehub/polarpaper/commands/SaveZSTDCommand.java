package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarGenerator;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.source.BytesPolarSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class SaveZSTDCommand {

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

        PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
        if (polarWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }
        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator == null) return Command.SINGLE_SUCCESS;

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Saving '", NamedTextColor.GRAY))
                        .append(Component.text(worldName, NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );



        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            Polar.updateConfig(bukkitWorld, bukkitWorld.getName()); // config should only be updated synchronously
        });

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (task) -> {

            for (int i = 0; i <= 22; i++) {
                long before = System.nanoTime();

                PolarGenerator generator = PolarGenerator.fromWorld(bukkitWorld);
                generator.setConfig(generator.getConfig().toBuilder().compressionLevel(i).build());

                BytesPolarSource source = new BytesPolarSource();
                try {
                    Polar.saveWorld(bukkitWorld, source);
                } catch (Exception e) {
                    String errorMsg = String.format("Failed to save '%s', please check logs for error", bukkitWorld.getName());
                    PolarPaper.logger().severe(errorMsg);
                    ctx.getSource().getSender().sendMessage(Component.text(errorMsg, NamedTextColor.RED));
                    return;
                }

                double ms = ((int) ((System.nanoTime() - before) / 1_000_0)) / 100.0;
                PolarPaper.logger().info("level: %s, %s bytes, %sms".formatted(i, source.bytes().length, ms));
            }


        });

        return Command.SINGLE_SUCCESS;
    }

}
