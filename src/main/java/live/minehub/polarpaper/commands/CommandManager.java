package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.builder.AbstractBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {

    private Component helpComponent = null;
    private final Map<String, PolarCmd> registeredCommands = new HashMap<>();
    private final LiteralArgumentBuilder<CommandSourceStack> rootCmd =
            Commands.literal("polar")
                    .requires(source -> source.getSender().hasPermission("polarpaper.use"));

    public void register(Commands registrar) {
        register(new BrowseCommand());
        register(new ConvertCommand());
        register(new CopyCommand());
        register(new CreateBlankCommand());
        register(new CreateFromRegionCommand());
        register(new DeleteCommand());
        register(new GotoCommand());
        register(new InfoCommand());
        register(new ListCommand());
        register(new LoadCommand());
        register(new PasteCommand());
        register(new ReloadCommand());
        register(new ReloadConfigCommand());
        register(new RenameCommand());
        register(new SaveCommand());
        register(new SetCenterCommand());
        register(new SetSpawnCommand());
        register(new UnloadCommand());
        register(new VersionCommand());

        register(new RelightCommand());

//        register(new GCCommand());
//        register(new SaveZSTDCommand());
//        register(new SetRandomCommand());
//        register(new FixSectionPaletteCommand());

        rootCmd
                .then(Commands.literal("help")
                        .requires(source -> source.getSender().hasPermission("polarpaper.help"))
                        .executes(this::executeHelp))
                .then(Commands.literal("wand")
                        .requires(source -> source.getSender().hasPermission("polarpaper.wand"))
                        .executes(WandCommand::wand))
                .then(Commands.literal("pos1")
                        .requires(source -> source.getSender().hasPermission("polarpaper.wand"))
                        .executes(WandCommand::pos1))
                .then(Commands.literal("pos2")
                        .requires(source -> source.getSender().hasPermission("polarpaper.wand"))
                        .executes(WandCommand::pos2));

        LiteralCommandNode<CommandSourceStack> build = rootCmd.build();

        TextComponent.Builder builder = Component.text();
        getUsageRecursive(builder, build, 1);
        helpComponent = ((AbstractBuilder<TextComponent>)builder).build();

        registrar.register(build);
    }

    private void register(PolarCmd polarCmd) {
        boolean contains = registeredCommands.containsKey(polarCmd.getName());
        if (contains) {
            PolarPaper.logger().warning("Already registered command with name: " + polarCmd.getName());
            return;
        }
        registeredCommands.put(polarCmd.getName(), polarCmd);

        polarCmd.registerCommand(rootCmd);
    }

    private int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(helpComponent);
        return Command.SINGLE_SUCCESS;
    }

    private void getUsageRecursive(TextComponent.Builder builder, CommandNode<CommandSourceStack> node, int depth) {
        if (depth != 1) {
            builder.appendSpace();

            boolean literal = node instanceof LiteralCommandNode<CommandSourceStack>;
            if (literal && depth > 2) builder.append(Component.text("<"));
            builder.append(Component.text(node.getUsageText()));
            if (literal && depth > 2) builder.append(Component.text(">"));
        }

        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (depth == 1) builder.append(Component.text("/polar", NamedTextColor.GRAY));
            getUsageRecursive(builder, child, depth + 1);
            if (depth == 1) {
                String subcommandName = child.getUsageText().replace("<", "").replace(">", "");
                PolarCmd cmd = registeredCommands.get(subcommandName);
                if (cmd != null) {
                    builder.appendNewline();
                    builder.append(Component.text(cmd.getDescription(), NamedTextColor.GRAY));
                }
            }
            if (depth == 1) builder.appendNewline();
        }
    }

}
