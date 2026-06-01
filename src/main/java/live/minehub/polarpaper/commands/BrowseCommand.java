package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.generator.PolarGenerator;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class BrowseCommand extends PolarCmd {

    private static final int ITEMS_PER_PAGE = 10;

    public BrowseCommand() {
        super("browse", "Browse your polar worlds folder");
    }

    public int execute(CommandContext<CommandSourceStack> ctx, Path parent, int page) {
        boolean isConsole = ctx.getSource().getSender() instanceof ConsoleCommandSender;

        TextComponent.Builder builder = Component.text();

        parent = parent.normalize();

        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");

        parent = WorldKey.validatePath(ctx.getSource().getSender(), parent);
        if (parent == null) return Command.SINGLE_SUCCESS;
        if (!Files.exists(parent)) {
            ctx.getSource().getSender().sendMessage(Component.text("File '" + parent.getFileName() + "' does not exist", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        List<Path> paths;
        try (Stream<Path> list = Files.list(parent)) {
            paths = new ArrayList<>(list.sorted((a, b) -> {
                // sort folders above, then sort alphabetically
                boolean aFolder = Files.isDirectory(a);
                boolean bFolder = Files.isDirectory(b);
                if (aFolder == bFolder) {
                    return a.compareTo(b); // sort alphabetically
                }
                if (aFolder) return -1;
                return 1;
            }).toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int totalPages = (int)Math.ceil((float)paths.size() / ITEMS_PER_PAGE);
        if (page > totalPages) page = Math.max(totalPages, 1);

        // Header
        builder.append(Component.text("Browsing '", NamedTextColor.GRAY));
        boolean first = true;
        for (Path path : worldsFolder.getParent().relativize(parent)) {
            if (!first) builder.append(Component.text(" / ", NamedTextColor.GRAY));
            builder.append(Component.text(path.getFileName().toString(), NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.runCommand(first ? "/polar browse" : "/polar browse 1 " + path)));
            first = false;
        }
        builder.append(Component.text("' ", NamedTextColor.GRAY));

        if (totalPages > 1) {
            builder.append(Component.text("(Page: ", NamedTextColor.GRAY));
            builder.append(Component.text(page, NamedTextColor.GRAY));
            builder.append(Component.text("/", NamedTextColor.GRAY));
            builder.append(Component.text(totalPages, NamedTextColor.GRAY));
            builder.append(Component.text(")", NamedTextColor.GRAY));
        }

        List<Path> pagedPaths = ListCommand.getPagedList(paths, page, ITEMS_PER_PAGE);
        for (Path path : pagedPaths) {
            boolean folder = Files.isDirectory(path);
            if (!folder && !path.getFileName().toString().endsWith(".polar")) continue;

            String fileName = path.getFileName().toString().replaceAll(".polar$", ""); // $ means last occurrence

            if (folder) {
                builder.append(Component.text()
                        .appendNewline()
                        .append(Component.text(" \uD83D\uDDBF ", NamedTextColor.YELLOW)) // folder icon
                        .append(Component.text(fileName, NamedTextColor.YELLOW))
                        .clickEvent(ClickEvent.runCommand("/polar browse 1 " + worldsFolder.relativize(path))));
                continue;
            }

            String worldName = WorldKey.getWorldName(path);
            NamespacedKey worldKey = NamespacedKey.fromString(worldName, PolarPaper.getPlugin());
            World bukkitWorld = worldKey == null ? null : Bukkit.getWorld(worldKey);
            PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);

            TextColor color = NamedTextColor.WHITE;
            if (polarGenerator == null) color = NamedTextColor.GRAY;

            builder.appendNewline();
            builder.append(Component.text(" - ", NamedTextColor.WHITE));
            builder.append(Component.text(fileName, color));

            if (bukkitWorld == null) {
                if (ctx.getSource().getSender().hasPermission("polarpaper.load") && !isConsole) {
                    builder.appendSpace();
                    builder.appendSpace();
                    builder.append(Component.text("ʟᴏᴀᴅ", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/polar load " + worldsFolder.relativize(path)))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to load world"))));
                }
                continue;
            }

            if (ctx.getSource().getSender().hasPermission("polarpaper.goto") && !isConsole) {
                builder.appendSpace();
                builder.appendSpace();
                builder.append(Component.text("ɢᴏᴛᴏ", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar goto " + worldsFolder.relativize(path)))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport"))));
            }
        }

        if (pagedPaths.isEmpty()) {
            builder.appendNewline();
            builder.appendNewline();
            builder.append(Component.text(" ".repeat(15)));
            builder.append(Component.text("Empty folder", NamedTextColor.GRAY, TextDecoration.ITALIC));
        }

        if (!isConsole) {
            int usedLines = pagedPaths.size();
            if (usedLines == 0) usedLines = 2; // account for empty folder text
            for (int i = 0; i < ITEMS_PER_PAGE - usedLines; i++) { // padding
                builder.appendNewline();
            }

            String currentParent = worldsFolder.relativize(parent).toString();
            if (!currentParent.isBlank()) currentParent = " " + currentParent;

            if (page > 1) {
                builder.appendNewline();
                builder.append(Component.text("[←]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar browse " + (page - 1) + currentParent)));
            }
            if (page < totalPages) {
                if (page <= 1) {
                    builder.appendNewline();
                    builder.append(Component.text("    "));
                }
                builder.append(Component.text(" ".repeat(25) + "[→]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/polar browse " + (page + 1) + currentParent)));
            }
        }

        ctx.getSource().getSender().sendMessage(builder.build());

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        return execute(ctx, worldsFolder, 1);
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(ctx -> {
                    Integer page = ctx.getArgument("page", Integer.class);
                    if (page == null) page = 1;

                    Path pluginFolder = PolarPaper.getPlugin().getDataPath();
                    Path worldsFolder = pluginFolder.resolve("worlds");

                    return execute(ctx, worldsFolder, page);
                })
                .then(Commands.argument("path", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String path = ctx.getArgument("path", String.class);
                            Integer page = ctx.getArgument("page", Integer.class);
                            if (page == null) page = 1;

                            Path pluginFolder = PolarPaper.getPlugin().getDataPath();
                            Path worldsFolder = pluginFolder.resolve("worlds");

                            return execute(ctx, worldsFolder.resolve(path), page);
                        })));
    }
}
