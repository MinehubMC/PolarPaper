package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

public class FixSectionPaletteCommand extends PolarCmd {

    public FixSectionPaletteCommand() {
        super("fixsectionpalette", "Fix current section palette");
    }

    protected static void run(CommandContext<CommandSourceStack> ctx) {

        if (!(ctx.getSource().getSender() instanceof Player player)) return;

        CraftWorld world = (CraftWorld)player.getWorld();

        CraftChunk chunk = (CraftChunk) player.getChunk();
        ChunkAccess chunkAccess = chunk.getHandle(ChunkStatus.FULL);

        int sectionIndex = chunkAccess.getSectionIndex(player.getLocation().getBlockY());
        LevelChunkSection section = chunkAccess.getSection(sectionIndex);

        if (!(section.getStates().data.configuration() instanceof Configuration.Global)) {
            player.sendMessage(Component.text("Not global palette"));
            return;
        }
        BitStorage storage = section.getStates().data.storage();

        for (int i = 0; i < storage.getSize(); i++) {
            int i1 = storage.get(i);

            int offset = 0;
            if (i1 >= 1381) offset += 200; // 1381: Block{minecraft:note_block}[instrument=trumpet,note=0,powered=true]
            if (i1 >= 2322) offset += 1; // 2322: Block{minecraft:golden_dandelion}
            if (i1 >= 10642) offset += 1; // 10642: Block{minecraft:potted_golden_dandelion}

            if (offset != 0) storage.set(i, i1 + offset);
        }

        world.refreshChunk(chunk.getX(), chunk.getZ());

        player.sendMessage(Component.text("Offset palette"));
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        run(ctx);
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
    }
}
