package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.generator.PolarGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class ListCommand extends PolarCmd {

    private static final int ITEMS_PER_PAGE = 10;

    public ListCommand() {
        super("list", "List all polar worlds");
    }

    public int execute(CommandContext<CommandSourceStack> ctx, int page) {
        TextComponent.Builder builder = Component.text();
        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        Set<String> worlds = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            if (world == null) continue;
            worlds.add(world.getName());
        }

        try (Stream<Path> list = Files.list(worldsFolder)) {
            list.forEach(path -> {
                worlds.add(path.getFileName().toString().replaceAll(".polar$", "")); // $ means last occurrence
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int totalPages = (int)Math.ceil((float)worlds.size() / ITEMS_PER_PAGE);
        if (page > totalPages) page = Math.max(totalPages, 1);

        builder.append(Component.text("List of worlds: ", NamedTextColor.GRAY));
        builder.append(Component.text("(Page: ", NamedTextColor.GRAY));
        builder.append(Component.text(page, NamedTextColor.GRAY));
        builder.append(Component.text("/", NamedTextColor.GRAY));
        builder.append(Component.text(totalPages, NamedTextColor.GRAY));
        builder.append(Component.text(")", NamedTextColor.GRAY));

        List<String> pagedWorlds = getPagedWorlds(new ArrayList<>(worlds), page, ITEMS_PER_PAGE);
        for (String world : pagedWorlds) {
            World bukkitWorld = Bukkit.getWorld(world);
            PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);

            TextColor color = NamedTextColor.WHITE;
//            if (bukkitWorld == null) color = NamedTextColor.GRAY;
            if (polarGenerator == null) color = NamedTextColor.GRAY;

            builder.appendNewline();
            builder.append(Component.text(" - ", NamedTextColor.WHITE));
            builder.append(Component.text(world, color));

            if (ctx.getSource().getSender().hasPermission("polarpaper.goto")) {
                builder.appendSpace();
                builder.append(Component.text("[GOTO]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar goto " + world))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to go to world"))));
            }
        }
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

        ctx.getSource().getSender().sendMessage(builder.build());

        return Command.SINGLE_SUCCESS;
    }

    public static List<String> getPagedWorlds(List<String> bukkitWorlds, int page, int worldsPerPage) {
        int start = (page - 1) * worldsPerPage;
        int end = Math.min(bukkitWorlds.size(), start + worldsPerPage);

        List<String> worlds = new ArrayList<>();
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
