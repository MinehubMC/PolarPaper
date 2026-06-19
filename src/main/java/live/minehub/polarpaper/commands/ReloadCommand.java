package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.resources.Identifier;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

public class ReloadCommand extends PolarCmd {

    public ReloadCommand() {
        super("reload", "Unload and load a polar world from the worlds folder");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        Identifier worldId = ctx.getArgument("world name", Identifier.class);

        CommandSender sender = ctx.getSource().getSender();

        World bukkitWorld = WorldKey.getWorld(worldId);
        PolarGenerator generator = PolarGenerator.fromWorld(bukkitWorld);
        if (generator == null) {
            sender.sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldId.getPath(), NamedTextColor.RED))
                            .append(Component.text("' is not a polar world!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarSource source = generator.getSource();
        if (source == null) {
            sender.sendMessage(Component.text("No source is defined for this world", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String newWorldName = bukkitWorld.getKey().toString().replace(PolarPaper.getPlugin().namespace() + ":", "");

        UnloadCommand.unload(ctx, worldId, false, false).thenAccept(success -> {
            if (!success) return;

            LoadCommand.loadWorld(ctx, source, newWorldName);
        });

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar reload <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(true)
                .executes(ReloadCommand::run));
    }
}
