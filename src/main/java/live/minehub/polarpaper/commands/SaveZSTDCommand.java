package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.source.BytesPolarSource;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.resources.Identifier;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class SaveZSTDCommand extends PolarCmd {

    public SaveZSTDCommand() {
        super("savezstd", "Save with every levels of ZSTD");
    }

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        Identifier worldId = ctx.getArgument("world name", Identifier.class);

        World bukkitWorld = WorldKey.getWorld(worldId);
        if (bukkitWorld == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldId.getPath(), NamedTextColor.RED))
                            .append(Component.text("' does not exist!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldId.getPath(), NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Saving '", NamedTextColor.GRAY))
                        .append(Component.text(worldId.getPath(), NamedTextColor.GRAY))
                        .append(Component.text("'...", NamedTextColor.GRAY))
        );



        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            Polar.updateConfig(bukkitWorld, bukkitWorld.getKey().getKey()); // config should only be updated synchronously
        });

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), _ -> {

            for (int i = 0; i <= 22; i++) {
                long before = System.nanoTime();

                PolarGenerator generator = PolarGenerator.fromWorld(bukkitWorld);
                generator.setConfig(generator.getConfig().toBuilder().compressionLevel(i).build());

                BytesPolarSource source = new BytesPolarSource();
                try {
                    Polar.saveWorld(bukkitWorld, source);
                } catch (Exception e) {
                    String errorMsg = String.format("Failed to save '%s', please check logs for error", bukkitWorld.getKey().getKey());
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

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar savezstd <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(true)
                .executes(SaveZSTDCommand::run));
    }
}
