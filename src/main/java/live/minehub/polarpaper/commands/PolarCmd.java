package live.minehub.polarpaper.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.util.Format;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.resources.Identifier;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class PolarCmd {

    private static @Nullable List<Path> CACHED_WORLDS_FOLDER = null;
    private static long LAST_CACHED = 0L;

    private final String name;
    private final String description;
    public PolarCmd(String name, String description) {
        this.name = name;
        this.description = description;
    }

    protected abstract int executeDefault(CommandContext<CommandSourceStack> ctx);

    protected abstract void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder);

    public void registerCommand(LiteralArgumentBuilder<CommandSourceStack> rootBuilder) {
        LiteralArgumentBuilder<CommandSourceStack> commandArgument = Commands.literal(getName())
                .requires(source -> source.getSender().hasPermission(getPermission()))
                .executes(this::executeDefault);

        addToBuilder(commandArgument);
        rootBuilder.then(commandArgument.build());
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPermission() {
        return "polarpaper." + getName();
    }

    public RequiredArgumentBuilder<CommandSourceStack, String> createFileWorldNameArgument(boolean greedy) {
        return Commands.argument("world name", greedy ? StringArgumentType.greedyString() : StringArgumentType.string())
                .suggests((_, s) -> {
                    List<Path> list;
                    try {
                        list = listFilesCached();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    list.forEach(path -> {
                        if (!Format.isSupported(path)) return;
                        String worldName = path.getFileName().toString();

                        if (!worldName.toLowerCase().startsWith(s.getRemainingLowerCase())) return;

                        s.suggest(worldName);
                    });

                    return s.buildFuture();
                });
    }

    private static List<Path> listFilesCached() throws IOException {
        if (LAST_CACHED + 10000 > System.currentTimeMillis()) return CACHED_WORLDS_FOLDER;

        Path worldsFolder = PolarPaper.getWorldsPath();
        try (var filesStream = Files.list(worldsFolder)) {
            CACHED_WORLDS_FOLDER = filesStream.toList();
        }
        LAST_CACHED = System.currentTimeMillis();
        return CACHED_WORLDS_FOLDER;
    }

    public RequiredArgumentBuilder<CommandSourceStack, Identifier> createWorldNameArgument(boolean onlyPolar) {
        return Commands.argument("world name", IdentifierArgument.id())
                .suggests((_, s) -> {
                    for (World world : Bukkit.getWorlds()) {
                        if (onlyPolar) {
                            PolarGenerator polarGenerator = PolarGenerator.fromWorld(world);
                            if (polarGenerator == null) continue;
                        }

                        String worldKey = world.getKey().toString().replace(PolarPaper.getPlugin().namespace() + ":", "");

                        if (!worldKey.toLowerCase().startsWith(s.getRemainingLowerCase())
                            && !world.getKey().getKey().toLowerCase().startsWith(s.getRemainingLowerCase())) continue;

                        s.suggest(worldKey);
                    }
                    return s.buildFuture();
                });
    }
}
