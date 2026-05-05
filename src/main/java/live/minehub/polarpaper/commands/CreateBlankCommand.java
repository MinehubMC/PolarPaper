package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class CreateBlankCommand extends PolarCmd {

    public CreateBlankCommand() {
        super("createblank", "Create a blank world");
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getArgument("new world name", String.class);

        World bukkitWorld = Bukkit.getWorld(worldName);
        if (bukkitWorld != null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text()
                            .append(Component.text("World '", NamedTextColor.RED))
                            .append(Component.text(worldName, NamedTextColor.RED))
                            .append(Component.text("' already exists!", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config defaultConfig = Config.getDefaultConfig(fileConfig);
        Config.writeToConfig(fileConfig, worldName, defaultConfig);

        Polar.createWorld((byte[]) null, worldName, defaultConfig);
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Created blank world '", NamedTextColor.AQUA))
                        .append(Component.text(worldName, NamedTextColor.AQUA))
                        .append(Component.text("'. ", NamedTextColor.AQUA))
                        .append(Component.text("Use ", NamedTextColor.AQUA))
                        .append(
                                Component.text()
                                        .append(Component.text("/polar goto ", NamedTextColor.WHITE))
                                        .append(Component.text(worldName, NamedTextColor.WHITE))
                                        .clickEvent(ClickEvent.runCommand("/polar goto " + worldName))
                                        .hoverEvent(HoverEvent.showText(Component.text("Click to run")))
                                        .decorate(TextDecoration.UNDERLINED))
                        .append(Component.text(" to teleport now", NamedTextColor.AQUA))
        );


        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar createblank <worldname>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("new world name", StringArgumentType.greedyString())
                .executes(CreateBlankCommand::run));
    }
}
