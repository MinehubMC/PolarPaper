package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.schematic.Rotation;
import live.minehub.polarpaper.schematic.Schematic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class PolarCommand {

    public static void register(Commands registrar) {
        registrar.register(
                Commands.literal("polar")
                        .requires(source -> source.getSender().hasPermission("polarpaper.version"))
                        .executes(VersionCommand::run)
//                        .then(Commands.literal("gc") // TODO: remove
//                                .executes(GCCommand::run))
                        .then(Commands.literal("help")
                                .requires(source -> source.getSender().hasPermission("polarpaper.help"))
                                .executes(HelpCommand::run)
                        )
                        .then(Commands.literal("goto")
                                .requires(source -> source.getSender().hasPermission("polarpaper.goto"))
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
                                                if (world == null) continue;
//                                                PolarWorld polarWorld = PolarWorld.fromWorld(world);
//                                                if (polarWorld == null) continue;

                                                builder.suggest(world.getName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(GotoCommand::run)))
                        .then(Commands.literal("createblank")
                                .requires(source -> source.getSender().hasPermission("polarpaper.createblank"))
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
                                .requires(source -> source.getSender().hasPermission("polarpaper.save"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar save <worldname>", NamedTextColor.RED))
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
                                        .executes(SaveCommand::run)))
                        .then(Commands.literal("load")
                                .requires(source -> source.getSender().hasPermission("polarpaper.load"))
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
                                .requires(source -> source.getSender().hasPermission("polarpaper.unload"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar unload <worldname> [save]", NamedTextColor.RED))
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
                                .requires(source -> source.getSender().hasPermission("polarpaper.setspawn"))
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
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ListCommand::runPaged))
                        )
                        .then(Commands.literal("convert")
                                    .requires(source -> source.getSender().hasPermission("polarpaper.convert"))
                                    .executes(ctx -> {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text()
                                                        .append(Component.text("Usage: /polar convert <new worldname> <chunk radius> (While in a non-polar world) to convert the chunks around you", NamedTextColor.RED))
                                        );
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("newworldname", StringArgumentType.string())
                                            .then(Commands.argument("chunkradius", IntegerArgumentType.integer(1))
                                                        .executes(ConvertCommand::run)
                                            )
                                    )
                        )
                        .then(Commands.literal("paste")
                                .requires(source -> source.getSender().hasPermission("polarpaper.paste"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar paste <worldname> [rotation] [air ignore] (While in a world) to place a polar world at your current position", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.string())
                                        .executes(PasteCommand::run)
                                        .then(Commands.argument("rotation", StringArgumentType.string())
                                                .suggests((ctx, builder) -> {
                                                    for (Rotation rotation : Rotation.values()) {
                                                        builder.suggest(rotation.getFriendlyName());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(PasteCommand::runWithRotation)
                                                .then(Commands.argument("ignoreair", StringArgumentType.string())
                                                        .suggests((ctx, builder) -> {
                                                            for (Schematic.IgnoreAir ignoreAir : Schematic.IgnoreAir.values()) {
                                                                builder.suggest(ignoreAir.name().toLowerCase());
                                                            }
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(PasteCommand::runWithRotationAndAirIgnore)))))
                        .then(Commands.literal("wand")
                                .requires(source -> source.getSender().hasPermission("polarpaper.wand"))
                                .executes(WandCommand::wand))
                        .then(Commands.literal("pos1")
                                .requires(source -> source.getSender().hasPermission("polarpaper.wand"))
                                .executes(WandCommand::pos1))
                        .then(Commands.literal("pos2")
                                .requires(source -> source.getSender().hasPermission("polarpaper.wand"))
                                .executes(WandCommand::pos2))
                        .then(Commands.literal("crop")
                                .requires(source -> source.getSender().hasPermission("polarpaper.crop"))
                                .executes(CropCommand::run))
                        .then(Commands.literal("createfromregion")
                                .requires(source -> source.getSender().hasPermission("polarpaper.convert"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar createfromregion <new worldname> (While in a world) to create a new polar world from the selected region", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("newworldname", StringArgumentType.string())
                                    .executes(CreateFromRegionCommand::run)))
                        .then(Commands.literal("copy")
                                .requires(source -> source.getSender().hasPermission("polarpaper.copy"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar copy <worldname> <new worldname> to copy a polar world", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.string())
                                        .then(Commands.argument("newworldname", StringArgumentType.string())
                                                .executes(CopyCommand::run))))
                        .build()
        );
    }



}
