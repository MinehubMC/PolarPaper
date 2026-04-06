package live.minehub.polarpaper.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.generator.PolarGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public abstract class PolarCmd {

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
        return Commands.argument("worldname", greedy ? StringArgumentType.greedyString() : StringArgumentType.string())
                .suggests((ctx, s) -> {
                    Path pluginFolder = PolarPaper.getPlugin().getDataPath();
                    Path worldsFolder = pluginFolder.resolve("worlds");

                    // TODO: cache list?
                    try (Stream<Path> list = Files.list(worldsFolder)) {
                        list.forEach(path -> {
                            String worldName = path.getFileName().toString().replaceAll(".polar$", "");

                            if (!worldName.toLowerCase().startsWith(s.getRemainingLowerCase())) return;

                            s.suggest(worldName);
                        });
                    } catch (IOException e) {
//                        throw new RuntimeException(e);
                    }

                    return s.buildFuture();
                });
    }

    public RequiredArgumentBuilder<CommandSourceStack, String> createWorldNameArgument(boolean greedy, boolean onlyPolar) {
        return Commands.argument("worldname", greedy ? StringArgumentType.greedyString() : StringArgumentType.string())
                .suggests((ctx, s) -> {
                    for (World world : Bukkit.getWorlds()) {
                        if (onlyPolar) {
                            PolarGenerator polarGenerator = PolarGenerator.fromWorld(world);
                            if (polarGenerator == null) continue;
                        }

                        if (!world.getName().toLowerCase().startsWith(s.getRemainingLowerCase())) continue;

                        if (world.getName().contains(" ") && !greedy) {
                            s.suggest("\"" + world.getName() + "\"");
                        } else {
                            s.suggest(world.getName());
                        }
                    }
                    return s.buildFuture();
                });
    }
}
