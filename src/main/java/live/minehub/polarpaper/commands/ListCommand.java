package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarWorld;
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
import java.util.List;
import java.util.stream.Stream;

public class ListCommand {

    private static final int ITEMS_PER_PAGE = 10;

    public static int run(CommandContext<CommandSourceStack> ctx) {
        return run(ctx, 1);
    }

    public static int runPaged(CommandContext<CommandSourceStack> ctx) {
        Integer page = ctx.getArgument("page", Integer.class);
        if (page == null) page = 1;
        return run(ctx, page);
    }

    public static int run(CommandContext<CommandSourceStack> ctx, int page) {
        TextComponent.Builder builder = Component.text();
        Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");
        List<String> worlds = new ArrayList<>();

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

        for (String world : getPagedWorlds(worlds, page, ITEMS_PER_PAGE)) {
            World bukkitWorld = Bukkit.getWorld(world);
            PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);

            TextColor color = NamedTextColor.WHITE;
            if (bukkitWorld == null) color = NamedTextColor.GRAY;
            if (polarWorld == null) color = NamedTextColor.GRAY;

            builder.append(Component.newline());
            builder.append(Component.text(" - ", NamedTextColor.WHITE));
            builder.append(Component.text(world, color));

            if (ctx.getSource().getSender().hasPermission("polarpaper.goto")) {
                builder.appendSpace();
                builder.append(Component.text("[GOTO]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar goto " + world))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to go to world"))));
            }
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

}
