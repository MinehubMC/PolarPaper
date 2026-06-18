package live.minehub.polarpaper;

import io.papermc.paper.persistence.PersistentDataContainerView;
import live.minehub.polarpaper.commands.WandCommand;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.schematic.Schematic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

@SuppressWarnings("unused")
public class PolarListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) { // coral death
        PolarGenerator generator = PolarGenerator.fromWorld(event.getBlock().getWorld());
        if (generator == null) return;
        Config config = generator.getConfig();
        Object enabled = config.gamerules().get("blockFade");
        if (!(enabled instanceof Boolean enabledBool)) return;
        event.setCancelled(!enabledBool);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) { // liquid flow / dragon egg
        PolarGenerator generator = PolarGenerator.fromWorld(event.getBlock().getWorld());
        if (generator == null) return;
        Config config = generator.getConfig();
        Object enabled = config.gamerules().getOrDefault("liquidPhysics", true);
        if (!(enabled instanceof Boolean enabledBool)) return;
        event.setCancelled(!enabledBool);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) { // block placement rules
        PolarGenerator generator = PolarGenerator.fromWorld(event.getBlock().getWorld());
        if (generator == null) return;
        Config config = generator.getConfig();
        Object enabled = config.gamerules().getOrDefault("blockPhysics", true);
        if (!(enabled instanceof Boolean enabledBool)) return;
        event.setCancelled(!enabledBool);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) { // gravity blocks
        if (!event.getBlock().getType().hasGravity()) return;

        PolarGenerator generator = PolarGenerator.fromWorld(event.getBlock().getWorld());
        if (generator == null) return;
        Config config = generator.getConfig();
        Object enabled = config.gamerules().getOrDefault("blockGravity", true);
        if (!(enabled instanceof Boolean enabledBool)) return;
        event.setCancelled(!enabledBool);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        Polar.stopAutoSaveTask(event.getWorld().getKey());
    }


    // WAND
    @EventHandler(ignoreCancelled = true)
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        // clear wand properties
        PersistentDataContainer data = event.getPlayer().getPersistentDataContainer();
        data.remove(Schematic.POS_1_KEY);
        data.remove(Schematic.POS_2_KEY);
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // clear wand properties
        PersistentDataContainer data = event.getPlayer().getPersistentDataContainer();
        data.remove(Schematic.POS_1_KEY);
        data.remove(Schematic.POS_2_KEY);
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        PersistentDataContainerView itemData = item.getPersistentDataContainer();
        if (!itemData.has(WandCommand.ITEM_STACK_KEY)) return;

        PersistentDataContainer data = player.getPersistentDataContainer();

        Vector blockPos = event.getBlock().getLocation().toVector();
        int[] array = new int[] { blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ() };
        data.set(Schematic.POS_1_KEY, PersistentDataType.INTEGER_ARRAY, array);

        event.setCancelled(true);
        String formattedPos = String.format("%s, %s, %s", blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ());
        player.sendMessage(Component.text("Set first corner position to " + formattedPos, NamedTextColor.AQUA));
    }
    @EventHandler
    public void onBlockBreak(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        PersistentDataContainerView itemData = item.getPersistentDataContainer();
        if (!itemData.has(WandCommand.ITEM_STACK_KEY)) return;

        PersistentDataContainer data = player.getPersistentDataContainer();

        Vector blockPos = event.getClickedBlock().getLocation().toVector();
        int[] array = new int[] { blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ() };
        data.set(Schematic.POS_2_KEY, PersistentDataType.INTEGER_ARRAY, array);

        event.setCancelled(true);
        String formattedPos = String.format("%s, %s, %s", blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ());
        player.sendMessage(Component.text("Set second corner position to " + formattedPos, NamedTextColor.AQUA));
    }

}
