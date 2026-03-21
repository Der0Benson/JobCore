package de.derbenson.jobcore;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class MinerListener implements Listener {

    private final JobManager jobManager;
    private final PlacedBlockTracker placedBlockTracker;
    private final QuestManager questManager;

    public MinerListener(
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
        final Job job = Job.MINER;

        if (!jobManager.isTrackedMaterial(job, block.getType())) {
            return;
        }

        if (placedBlockTracker.isPlayerPlaced(block)) {
            placedBlockTracker.unmarkPlayerPlaced(block);
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

