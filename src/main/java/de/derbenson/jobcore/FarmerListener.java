package de.derbenson.jobcore;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class FarmerListener implements Listener {

    private final JobManager jobManager;
    private final PlacedBlockTracker placedBlockTracker;
    private final QuestManager questManager;

    public FarmerListener(
            final JobManager jobManager,
            final PlacedBlockTracker placedBlockTracker,
            final QuestManager questManager
    ) {
        this.jobManager = jobManager;
        this.placedBlockTracker = placedBlockTracker;
        this.questManager = questManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final Job job = Job.FARMER;

        if (!jobManager.isTrackedMaterial(job, block.getType())) {
            return;
        }

        if (placedBlockTracker.isPlayerPlaced(block)) {
            placedBlockTracker.unmarkPlayerPlaced(block);
            return;
        }

        if (!canAwardBreakExperience(job, block)) {
            return;
        }

        final int xp = jobManager.getConfiguredXpValue(job, block.getType());
        if (xp <= 0) {
            return;
        }

        final boolean doubleDrops = event.isDropItems() && jobManager.shouldDoubleDrops(player.getUniqueId(), job);
        final Optional<BonusDropEntry> bonusDrop = event.isDropItems()
                ? jobManager.rollBonusDrop(player.getUniqueId(), job)
                : Optional.empty();

        final int granted = jobManager.grantExperience(player, job, xp);
        applyPerkDrops(event, block, player, doubleDrops, bonusDrop);
        if (granted > 0) {
            questManager.recordObjective(player, job, QuestObjectiveType.BREAK_BLOCK, block.getType().name(), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SWEET_BERRY_BUSH) {
            return;
        }

        if (!jobManager.isTrackedMaterial(Job.FARMER, block.getType()) || !isMature(block)) {
            return;
        }

        if (placedBlockTracker.isPlayerPlaced(block)) {
            return;
        }

        final Player player = event.getPlayer();
        final int xp = jobManager.getConfiguredXpValue(Job.FARMER, block.getType());
        if (xp <= 0) {
            return;
        }

        final boolean doubleDrops = jobManager.shouldDoubleDrops(player.getUniqueId(), Job.FARMER);
        final Optional<BonusDropEntry> bonusDrop = jobManager.rollBonusDrop(player.getUniqueId(), Job.FARMER);

        final int granted = jobManager.grantExperience(player, Job.FARMER, xp);
        if (granted > 0) {
            questManager.recordObjective(player, Job.FARMER, QuestObjectiveType.BREAK_BLOCK, block.getType().name(), 1);
        }

        if (doubleDrops) {
            final int amount = 1 + ThreadLocalRandom.current().nextInt(2);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SWEET_BERRIES, amount));
        }

        bonusDrop.ifPresent(entry -> block.getWorld().dropItemNaturally(block.getLocation(), entry.toItemStack()));
    }

    private boolean canAwardBreakExperience(final Job job, final Block block) {
        if (!jobManager.requiresMatureHarvest(job, block.getType())) {
            return true;
        }
        return isMature(block);
    }

    private boolean isMature(final Block block) {
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return true;
        }
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private void applyPerkDrops(
            final BlockBreakEvent event,
            final Block block,
            final Player player,
            final boolean doubleDrops,
            final Optional<BonusDropEntry> bonusDrop
    ) {
        if (!event.isDropItems()) {
            return;
        }

        if (doubleDrops) {
            final ItemStack tool = player.getInventory().getItemInMainHand();
            for (final ItemStack drop : block.getDrops(tool, player)) {
                block.getWorld().dropItemNaturally(block.getLocation(), drop.clone());
            }
        }

        bonusDrop.ifPresent(entry -> block.getWorld().dropItemNaturally(block.getLocation(), entry.toItemStack()));
    }
}

