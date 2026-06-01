package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.generator.PolarGenerator;
import live.minehub.polarpaper.util.ExceptionUtil;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;

public class DeleteCommand extends PolarCmd {

    public DeleteCommand() {
        super("delete", "Delete a polar world");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world name", String.class);

        World world = WorldKey.getWorld(worldName);
        if (world == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        deleteWorld(ctx, world);

        return Command.SINGLE_SUCCESS;
    }

    private static int confirmMessage(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("world name", String.class);

        World world = WorldKey.getWorld(worldName);
        if (world == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' does not exist", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }
        PolarGenerator generator = PolarGenerator.fromWorld(world);
        if (generator == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Not deleting non-polar world '", NamedTextColor.RED))
                            .append(Component.text(world.getKey().getKey(), NamedTextColor.RED))
                            .append(Component.text("'", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Confirm deleting ", NamedTextColor.AQUA))
                        .append(Component.text("'", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'? ", NamedTextColor.AQUA))
                        .append(Component.text("CONFIRM", NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.runCommand("/polar delete \"" + worldName + "\" confirm")))
        );

        return Command.SINGLE_SUCCESS;
    }

    private static void deleteWorld(CommandContext<CommandSourceStack> ctx, World world) {
        PolarGenerator generator = PolarGenerator.fromWorld(world);
        if (generator == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("Not deleting non-polar world '", NamedTextColor.RED))
                            .append(Component.text(world.getKey().getKey(), NamedTextColor.RED))
                            .append(Component.text("'", NamedTextColor.RED))
            );
            return;
        }
        if (generator.getSource() == null) {
            ctx.getSource().getSender().sendMessage(Component.text("No source is defined for this world", NamedTextColor.RED));
            return;
        }

        try {
            generator.getSource().delete();
        } catch (Exception e) {
            if (e instanceof UnsupportedOperationException) {
                ctx.getSource().getSender().sendMessage(Component.text("This world's source does not support deleting", NamedTextColor.RED));
            } else {
                ctx.getSource().getSender().sendMessage(Component.text("Failed to delete world", NamedTextColor.RED));
                PolarPaper.logger().severe("Failed to delete world: " + world.getKey().getKey());
                ExceptionUtil.log(e);
            }
            return;
        }

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Deleted '", NamedTextColor.AQUA))
                        .append(Component.text(world.getKey().getKey(), NamedTextColor.AQUA))
                        .append(Component.text("'!", NamedTextColor.AQUA))
        );

        UnloadCommand.bukkitUnload(ctx, world);
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar delete <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(false, true)
                .executes(DeleteCommand::confirmMessage)
                .then(Commands.literal("confirm")
                    .executes(DeleteCommand::run)));
    }
}
