package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public class GCCommand {

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage("Running GC...");
        System.gc();
        ctx.getSource().getSender().sendMessage("Ran GC!");

        return Command.SINGLE_SUCCESS;
    }

}
