package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class PolarCommand {

    public static void register(Commands registrar) {
        registrar.register(
                Commands.literal("polar")
                        .requires(source -> source.getSender().hasPermission("paperpolar.version"))
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(
                                    Component.text()
                                            .append(Component.text("Polar for Paper v", NamedTextColor.AQUA))
                                            .append(Component.text(PolarPaper.getPlugin().getPluginMeta().getVersion(), NamedTextColor.AQUA))
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("help")
                                .requires(source -> source.getSender().hasPermission("paperpolar.help"))
                                .executes(HelpCommand::run)
                        )
                        .then(Commands.literal("goto")
                                .requires(source -> source.getSender().hasPermission("paperpolar.goto"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar goto <worldname>", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> {
                                            for (World world : Bukkit.getWorlds()) {
//                                                PolarWorld polarWorld = PolarWorld.fromWorld(world);
//                                                if (polarWorld == null) continue;

                                                builder.suggest(world.getName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(GotoCommand::run)))
                        .then(Commands.literal("createblank")
                                .requires(source -> source.getSender().hasPermission("paperpolar.createblank"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar createblank <worldname>", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.greedyString())
                                        .executes(CreateBlankCommand::run)))
                        .then(Commands.literal("save")
                                .requires(source -> source.getSender().hasPermission("paperpolar.save"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar save <worldname> to save all chunks\n", NamedTextColor.RED))
                                                    .append(Component.text(" OR /polar save <worldname> [chunk radius] [centered] to save a region", NamedTextColor.RED))
                                                    .append(Component.text("\nUse centered to center the saved radius around your player", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            for (World world : Bukkit.getWorlds()) {
                                                PolarWorld polarWorld = PolarWorld.fromWorld(world);
                                                if (polarWorld == null) continue;

                                                if (world.getName().contains(" ")) {
                                                    builder.suggest("\"" + world.getName() + "\"");
                                                } else {
                                                    builder.suggest(world.getName());
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(SaveCommand::run)
                                        .then(Commands.argument("chunkradius", IntegerArgumentType.integer(1))
                                                .executes(SaveCommand::runSelected)
                                                .then(Commands.literal("centered")
                                                        .executes(SaveCommand::runSelectedCentered))
                                        )))
                        .then(Commands.literal("load")
                                .requires(source -> source.getSender().hasPermission("paperpolar.load"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar load <worldname>", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.greedyString())
                                        .executes(LoadCommand::run)))
                        .then(Commands.literal("unload")
                                .requires(source -> source.getSender().hasPermission("paperpolar.load"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar load <worldname> [save]", NamedTextColor.RED))
                                                    .append(Component.text("Setting save will override config", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.string())
                                        .suggests((ctx, builder) -> {
                                            for (World world : Bukkit.getWorlds()) {
                                                PolarWorld polarWorld = PolarWorld.fromWorld(world);
                                                if (polarWorld == null) continue;

                                                if (world.getName().contains(" ")) {
                                                    builder.suggest("\"" + world.getName() + "\"");
                                                } else {
                                                    builder.suggest(world.getName());
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(UnloadCommand::run)
                                        .then(Commands.argument("save", BoolArgumentType.bool())
                                                .executes(UnloadCommand::runOverrided))
                                ))
                        .then(Commands.literal("info")
                                .requires(source -> source.getSender().hasPermission("polarpaper.info"))
                                .executes(InfoCommand::run)
                                .then(Commands.argument("worldname", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> {
                                            for (World world : Bukkit.getWorlds()) {
                                                PolarWorld polarWorld = PolarWorld.fromWorld(world);
                                                if (polarWorld == null) continue;

                                                builder.suggest(world.getName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(InfoCommand::runArg)))
                        .then(Commands.literal("setspawn")
                                .requires(source -> source.getSender().hasPermission("polarpaper.info"))
                                .executes(ctx -> SetSpawnCommand.run(ctx, false))
                                .then(Commands.literal("rounded")
                                        .executes(ctx -> SetSpawnCommand.run(ctx, true)))
                        )
                        .then(Commands.literal("reloadconfig")
                                .requires(source -> source.getSender().hasPermission("polarpaper.reloadconfig"))
                                .executes(ReloadConfigCommand::run)
                        )
                        .then(Commands.literal("list")
                                .requires(source -> source.getSender().hasPermission("polarpaper.list"))
                                .executes(ListCommand::run)
                        )
                        .then(Commands.literal("convert")
                                    .requires(source -> source.getSender().hasPermission("polarpaper.convert"))
                                    .executes(ctx -> {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text()
                                                        .append(Component.text("Usage: /polar convert <new worldname> <chunk radius> (While in a non-polar world) to convert the chunks around you", NamedTextColor.RED))
                                                        .append(Component.text(" OR: /polar convert <new worldname> <chunk radius> centered (While in a non-polar world) to center the new converted chunks at 0,0", NamedTextColor.RED))
                                        );
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("newworldname", StringArgumentType.string())
                                            .then(Commands.argument("chunkradius", IntegerArgumentType.integer(1))
                                                        .executes(ConvertCommand::run)
                                                        .then(Commands.literal("centered")
                                                                    .executes(ConvertCommand::runCentered))
                                            )
                                    )
                        )
                        .build()
        );
    }



}
