package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SetRandomCommand extends PolarCmd {

    public SetRandomCommand() {
        super("setrandom", "Set current section with random blocks");
    }

    protected static int run(CommandContext<CommandSourceStack> ctx) {

        if (!(ctx.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;

        World world = player.getWorld();

        Integer numBlocks = ctx.getArgument("num blocks", Integer.class);

        List<BlockState> states = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            states.addAll(block.getStateDefinition().getPossibleStates());
        }

        int startX = player.getChunk().getX() * 16;
        int startZ = player.getChunk().getZ() * 16;

        Collections.shuffle(states);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        int i = 0;

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockState randomMat = states.remove(random.nextInt(states.size()));

                    i++;

                    world.getBlockAt(startX + x, y, startZ + z).setBlockData(randomMat.asBlockData());
                    if (i >= numBlocks) return Command.SINGLE_SUCCESS;
                }
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("Usage: /polar setrandom <num blocks>", NamedTextColor.RED))
        );
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.argument("num blocks", IntegerArgumentType.integer(1, 4096))
                .executes(SetRandomCommand::run));
    }
}
