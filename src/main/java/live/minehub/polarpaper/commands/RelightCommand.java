package live.minehub.polarpaper.commands;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

public class RelightCommand extends PolarCmd {

    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));

    public RelightCommand() {
        super("relight", "Relight the world");
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) return Command.SINGLE_SUCCESS;

        ServerLevel world = ((CraftWorld) player.getWorld()).getHandle();
        ThreadedLevelLightEngine lightEngine = world.getChunkSource().getLightEngine();

        starlightFixLight(player, world, lightEngine);

        return Command.SINGLE_SUCCESS;
    }

    private void starlightFixLight(CommandSender sender, ServerLevel world, ThreadedLevelLightEngine lightEngine) {
        long start = System.nanoTime();

        List<NewChunkHolder> chunkHolders = world.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolders();

        LinkedHashSet<ChunkPos> chunks = new LinkedHashSet<>();

        for (NewChunkHolder chunkHolder : chunkHolders) {
            chunks.add(new ChunkPos(chunkHolder.chunkX, chunkHolder.chunkZ));
        }
        int[] pending = new int[1];
        Iterator<ChunkPos> iterator = chunks.iterator();

        while (iterator.hasNext()) {
            ChunkPos chunkPos = iterator.next();
            ChunkAccess chunk = (ChunkAccess) world.getChunkSource().getChunkForLighting(chunkPos.x(), chunkPos.z());
            if (chunk != null && chunk.isLightCorrect() && chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
                pending[0]++;
            } else {
                iterator.remove();
            }
        }

        long[] lastSentMsg = new long[1];
        int[] relitChunks = new int[1];
        lightEngine.starlight$serverRelightChunks(chunks, _ -> {
            relitChunks[0]++;

            if (lastSentMsg[0] > System.currentTimeMillis()) return;
            lastSentMsg[0] = System.currentTimeMillis() + 500; // send message every 500ms

            String percent = ONE_DECIMAL_PLACES.get().format((double)100.0F * (double)relitChunks[0] / (double)pending[0]);
            TextComponent.Builder message = Component.text().color(NamedTextColor.DARK_AQUA)
                    .append(Component.text("Progress: ", NamedTextColor.BLUE))
                    .append(Component.text(percent + "%"));
            sender.sendMessage(message);
        }, (totalRelit) -> {
            long end = System.nanoTime();
            sender.sendMessage(Component.text().color(NamedTextColor.DARK_AQUA)
                    .append(Component.text("Relit ", NamedTextColor.BLUE))
                    .append(Component.text(totalRelit), Component.text(" chunks. Took ", NamedTextColor.BLUE))
                    .append(Component.text(ONE_DECIMAL_PLACES.get().format(1.0E-6 * (double)(end - start)) + "ms")));
        });
        sender.sendMessage(Component.text().color(NamedTextColor.BLUE)
                .append(Component.text("Relighting "))
                .append(Component.text(pending[0], NamedTextColor.DARK_AQUA))
                .append(Component.text(" chunks")));
    }
    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {

    }
}
