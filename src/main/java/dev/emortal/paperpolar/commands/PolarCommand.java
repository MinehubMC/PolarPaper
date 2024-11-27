package dev.emortal.paperpolar.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.emortal.paperpolar.PolarWorld;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

@SuppressWarnings("UnstableApiUsage")
public class PolarCommand {

    public static void register(Commands registrar) {
        registrar.register(
                Commands.literal("polar")
                        .requires(source -> source.getSender().hasPermission("paperpolar.version"))
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(
                                    Component.text()
                                            .append(Component.text("Polar for Paper", NamedTextColor.AQUA))
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("goto")
                                .requires(source -> source.getSender().hasPermission("paperpolar.goto"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar goto <worldname>", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (World world : Bukkit.getWorlds()) {
                                                PolarWorld polarWorld = PolarWorld.fromWorld(world);
                                                if (polarWorld == null) continue;

                                                builder.suggest(world.getName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(GotoCommand::run)))
                        .then(Commands.literal("save")
                                .requires(source -> source.getSender().hasPermission("paperpolar.save"))
                                .executes(ctx -> {
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text()
                                                    .append(Component.text("Usage: /polar save <worldname>", NamedTextColor.RED))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("worldname", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (World world : Bukkit.getWorlds()) {
                                                PolarWorld polarWorld = PolarWorld.fromWorld(world);
                                                if (polarWorld == null) continue;

                                                builder.suggest(world.getName());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(SaveCommand::run)))
                        .then(
                                Commands.literal("info")
                                .requires(source -> source.getSender().hasPermission("paperpolar.info"))
                                .executes(InfoCommand::run)
                        )
                        .then(Commands.literal("convert")
                                    .requires(source -> source.getSender().hasPermission("paperpolar.convert"))
                                    .executes(ctx -> {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text()
                                                        .append(Component.text("Usage: /polar convert <new worldname> <chunk radius> (While in a non-polar world) to convert the chunks around you", NamedTextColor.RED))
                                                        .append(Component.text(" OR: /polar convert <new worldname> <chunk radius> centered (While in a non-polar world) to center the converted chunks at 0,0", NamedTextColor.RED))
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
