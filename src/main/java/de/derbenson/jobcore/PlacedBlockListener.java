package de.derbenson.jobcore;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class PlacedBlockListener implements Listener {

    private final JobManager jobManager;
    private final PlacedBlockTracker placedBlockTracker;

    public PlacedBlockListener(final JobManager jobManager, final PlacedBlockTracker placedBlockTracker) {
        this.jobManager = jobManager;
        this.placedBlockTracker = placedBlockTracker;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (jobManager.shouldTrackPlacedBlock(event.getBlockPlaced().getType())) {
            placedBlockTracker.markAsPlayerPlaced(event.getBlockPlaced());
        }
    }
}

