package live.minehub.polarpaper.core.util;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
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
                BlockState state = ((CraftBlockData) Bukkit.getServer().createBlockData(a)).getState();
                BLOCK_TO_STRING.put(state, string); // populate opposite map
                return state;
            } catch (IllegalArgumentException _) {
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
