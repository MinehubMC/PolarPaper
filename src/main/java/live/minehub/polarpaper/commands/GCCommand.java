package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public class GCCommand extends PolarCmd {

    public GCCommand() {
        super("gc", "Run a garbage collection");
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage("Running GC...");
        printMemoryStats(ctx);
        System.gc();
        ctx.getSource().getSender().sendMessage("Ran GC!");
        printMemoryStats(ctx);

        return Command.SINGLE_SUCCESS;
    }

    private static void printMemoryStats(CommandContext<CommandSourceStack> ctx) {
        Runtime runtime = Runtime.getRuntime();
        long memory = runtime.totalMemory() - runtime.freeMemory();

        ctx.getSource().getSender().sendMessage("Currently using %sMB".formatted(memory / (1024L * 1024L)));
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {

    }
}
