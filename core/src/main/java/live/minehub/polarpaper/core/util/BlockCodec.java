package live.minehub.polarpaper.core.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockCodec.class);

    private static final Map<String, BlockState> STRING_TO_BLOCK = new ConcurrentHashMap<>();
    private static final Map<BlockState, String> BLOCK_TO_STRING = new ConcurrentHashMap<>();

    public static BlockState blockFromString(String string) {
        return STRING_TO_BLOCK.computeIfAbsent(string, a -> {
            try {
                StringReader reader = new StringReader(a);
                BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, reader, false).blockState();
                if (reader.canRead()) throw new IllegalArgumentException("Trailing data: " + reader.getRemaining());
                BLOCK_TO_STRING.put(state, string); // populate opposite map
                return state;
            } catch (CommandSyntaxException | IllegalArgumentException _) {
                LOGGER.warn("Failed to parse block state: {}", a);
                return Blocks.AIR.defaultBlockState();
            }
        });
    }

    public static String stringFromBlock(BlockState block) {
        return BLOCK_TO_STRING.computeIfAbsent(block, a -> {
            // Block{minecraft:oak_fence}[...] to minecraft:oak_fence[...]
            String string = a.toString().replace("Block{", "").replace("}", "");
            STRING_TO_BLOCK.put(string, a); // populate opposite map
            return string;
        });
    }

}
