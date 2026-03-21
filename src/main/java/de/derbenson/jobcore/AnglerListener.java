package de.derbenson.jobcore;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AnglerListener implements Listener {

    private final JobManager jobManager;
    private final ConfigManager configManager;
    private final DebugManager debugManager;
    private final QuestManager questManager;
    private final ConcurrentHashMap<UUID, ArrayDeque<CatchRecord>> catchHistory = new ConcurrentHashMap<>();
    private final Map<UUID, CastRecord> castRecords = new HashMap<>();

    public AnglerListener(
            final JobManager jobManager,
            final ConfigManager configManager,
            final DebugManager debugManager,
            final QuestManager questManager
    ) {
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.debugManager = debugManager;
        this.questManager = questManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUuid = player.getUniqueId();

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            castRecords.put(playerUuid, new CastRecord(
                    System.currentTimeMillis(),
                    player.getLocation().clone(),
                    event.getHook().getLocation().clone()
            ));
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            castRecords.remove(playerUuid);
            return;
        }

        if (!(event.getCaught() instanceof Item item)) {
            castRecords.remove(playerUuid);
            return;
        }

        final Material material = item.getItemStack().getType();
        final Job job = Job.ANGLER;

        if (!jobManager.isTrackedMaterial(job, material)) {
            castRecords.remove(playerUuid);
            return;
        }

        final int baseXp = jobManager.getConfiguredXpValue(job, material);
        if (baseXp <= 0) {
            castRecords.remove(playerUuid);
            return;
        }

        final CastRecord castRecord = castRecords.remove(playerUuid);
        final String blockedReason = getBlockedReason(player, event, castRecord);
        if (blockedReason != null) {
            debugManager.sendXpDebug(player.getName() + " erhielt keine Angler-XP für " + material.name() + " wegen " + blockedReason + ".");
            return;
        }

        final AntiFarmResult antiFarmResult = applySameSpotAntiFarm(player, event, baseXp);
        if (antiFarmResult.adjustedXp() <= 0) {
            debugManager.sendXpDebug(player.getName() + " erhielt keine Angler-XP für " + material.name() + ", weil Anti-Farm aktiv war.");
            return;
        }

        final boolean doubleDrops = jobManager.shouldDoubleDrops(playerUuid, job);
        final Optional<BonusDropEntry> bonusDrop = jobManager.rollBonusDrop(playerUuid, job);

        final int granted = jobManager.grantExperience(player, job, antiFarmResult.adjustedXp());
        if (granted > 0) {
            questManager.recordObjective(player, job, QuestObjectiveType.FISH_ITEM, material.name(), 1);
            final String suffix = antiFarmResult.reduced()
                    ? " (Anti-Farm aktiv, Basis " + baseXp + " -> " + antiFarmResult.adjustedXp() + ")"
                    : "";
            debugManager.sendXpDebug(player.getName() + " erhielt " + granted + " Angler-XP für " + material.name() + suffix + ".");
        }

        if (doubleDrops) {
            item.getWorld().dropItemNaturally(item.getLocation(), item.getItemStack().clone());
        }

        bonusDrop.ifPresent(entry -> item.getWorld().dropItemNaturally(item.getLocation(), new ItemStack(entry.material(), entry.amount())));
    }

    private String getBlockedReason(
            final Player player,
            final PlayerFishEvent event,
            final CastRecord castRecord
    ) {
        if (configManager.getJobConfiguration(Job.ANGLER).getBoolean("anti-farm.require-open-water", true)
                && !event.getHook().isInOpenWater()) {
            return "fehlendem Open-Water";
        }

        if (castRecord == null) {
            return "fehlender Wurf-Historie";
        }

        final double minimumCastSeconds = Math.max(0.0D, configManager.getJobConfiguration(Job.ANGLER).getDouble("anti-farm.minimum-cast-seconds", 3.0D));
        if ((System.currentTimeMillis() - castRecord.timestamp()) < Math.round(minimumCastSeconds * 1000.0D)) {
            return "zu kurzer Fangdauer";
        }

        final double maxMoveDistance = Math.max(0.0D, configManager.getJobConfiguration(Job.ANGLER).getDouble("anti-farm.max-player-move-distance", 3.0D));
        if (!player.getWorld().equals(castRecord.playerLocation().getWorld())
                || player.getLocation().distanceSquared(castRecord.playerLocation()) > (maxMoveDistance * maxMoveDistance)) {
            return "zu großer Bewegung während des Wurfs";
        }

        return null;
    }

    private AntiFarmResult applySameSpotAntiFarm(final Player player, final PlayerFishEvent event, final int baseXp) {
        final long windowMillis = Math.max(1L, configManager.getJobConfiguration(Job.ANGLER).getLong("anti-farm.same-spot.window-seconds", 60L)) * 1000L;
        final double hookRadius = Math.max(0.0D, configManager.getJobConfiguration(Job.ANGLER).getDouble("anti-farm.same-spot.hook-radius", 1.5D));
        final double playerRadius = Math.max(0.0D, configManager.getJobConfiguration(Job.ANGLER).getDouble("anti-farm.same-spot.player-radius", 1.5D));
        final int threshold = Math.max(1, configManager.getJobConfiguration(Job.ANGLER).getInt("anti-farm.same-spot.threshold", 8));
        final double multiplier = Math.max(0.0D, configManager.getJobConfiguration(Job.ANGLER).getDouble("anti-farm.same-spot.multiplier", 0.5D));
        final long now = System.currentTimeMillis();

        final ArrayDeque<CatchRecord> history = catchHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        while (!history.isEmpty() && (now - history.peekFirst().timestamp()) > windowMillis) {
            history.pollFirst();
        }

        final Location playerLocation = player.getLocation();
        final Location hookLocation = event.getHook().getLocation();
        int similarCatches = 1;
        final double hookRadiusSquared = hookRadius * hookRadius;
        final double playerRadiusSquared = playerRadius * playerRadius;

        for (final CatchRecord record : history) {
            if (!record.playerLocation().getWorld().equals(playerLocation.getWorld())) {
                continue;
            }
            if (record.playerLocation().distanceSquared(playerLocation) <= playerRadiusSquared
                    && record.hookLocation().distanceSquared(hookLocation) <= hookRadiusSquared) {
                similarCatches++;
            }
        }

        history.addLast(new CatchRecord(now, playerLocation.clone(), hookLocation.clone()));
        if (similarCatches > threshold) {
            final int adjusted = Math.max(multiplier > 0.0D ? 1 : 0, (int) Math.floor(baseXp * multiplier));
            return new AntiFarmResult(adjusted, true);
        }

        return new AntiFarmResult(baseXp, false);
    }

    private record CatchRecord(long timestamp, Location playerLocation, Location hookLocation) {
    }

    private record CastRecord(long timestamp, Location playerLocation, Location hookLocation) {
    }

    private record AntiFarmResult(int adjustedXp, boolean reduced) {
    }
}

