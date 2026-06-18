package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;

import java.util.ArrayList;
import java.util.List;

public class ListCommand extends PolarCmd {

    private static final int ITEMS_PER_PAGE = 10;

    public ListCommand() {
        super("list", "List all loaded worlds");
    }

    public int execute(CommandContext<CommandSourceStack> ctx, int page) {
        boolean isConsole = ctx.getSource().getSender() instanceof ConsoleCommandSender;

        TextComponent.Builder builder = Component.text();
        List<World> worlds = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            if (world == null) continue;
            worlds.add(world);
        }

        int totalPages = (int)Math.ceil((float)worlds.size() / ITEMS_PER_PAGE);
        if (page > totalPages) page = Math.max(totalPages, 1);

        builder.append(Component.text("Loaded worlds: ", NamedTextColor.GRAY));
        builder.append(Component.text("(Page: ", NamedTextColor.GRAY));
        builder.append(Component.text(page, NamedTextColor.GRAY));
        builder.append(Component.text("/", NamedTextColor.GRAY));
        builder.append(Component.text(totalPages, NamedTextColor.GRAY));
        builder.append(Component.text(")", NamedTextColor.GRAY));

        List<World> pagedWorlds = getPagedList(worlds, page, ITEMS_PER_PAGE);
        for (World world : pagedWorlds) {
            PolarGenerator polarGenerator = PolarGenerator.fromWorld(world);

            TextColor color = NamedTextColor.WHITE;
            if (polarGenerator == null) color = NamedTextColor.GRAY;

            String worldName = world.getKey().toString()
                    .replace(PolarPaper.getPlugin().namespace() + ":", ""); // don't show Polar's namespace
            builder.appendNewline();
            builder.append(Component.text(" - ", NamedTextColor.WHITE));
            builder.append(Component.text(worldName, color));

            if (ctx.getSource().getSender().hasPermission("polarpaper.goto") && !isConsole) {
                builder.appendSpace();
                builder.appendSpace();
                builder.append(Component.text("ɢᴏᴛᴏ", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar goto " + world.getKey()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport"))));
            }
        }

        if (!isConsole) {
            for (int i = 0; i < ITEMS_PER_PAGE - pagedWorlds.size(); i++) {
                builder.appendNewline();
            }

            if (page > 1) {
                builder.appendNewline();
                builder.append(Component.text("[←]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar list " + (page - 1))));
            }
            if (page < totalPages) {
                if (page <= 1) {
                    builder.appendNewline();
                    builder.append(Component.text("    "));
                }
                builder.append(Component.text(" ".repeat(25) + "[→]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar list " + (page + 1))));
            }
        }

        ctx.getSource().getSender().sendMessage(builder.build());

        return Command.SINGLE_SUCCESS;
    }

    public static <T> List<T> getPagedList(List<T> bukkitWorlds, int page, int worldsPerPage) {
        int start = (page - 1) * worldsPerPage;
        int end = Math.min(bukkitWorlds.size(), start + worldsPerPage);

        List<T> worlds = new ArrayList<>();
        for (int i = start; i < end; i++) {
            worlds.add(bukkitWorlds.get(i));
        }
        return worlds;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        return execute(ctx, 1);
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(ctx -> {
                    Integer page = ctx.getArgument("page", Integer.class);
                    if (page == null) page = 1;
                    return execute(ctx, page);
                }));
    }
}
