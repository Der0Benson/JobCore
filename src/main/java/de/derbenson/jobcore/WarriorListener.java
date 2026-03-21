package de.derbenson.jobcore;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarriorListener implements Listener {

    private final JobManager jobManager;
    private final ConfigManager configManager;
    private final DebugManager debugManager;
    private final QuestManager questManager;
    private final NamespacedKey blockedXpKey;
    private final Map<UUID, ArrayDeque<KillRecord>> killHistory = new ConcurrentHashMap<>();

    public WarriorListener(
            final JavaPlugin plugin,
            final JobManager jobManager,
            final ConfigManager configManager,
            final DebugManager debugManager,
            final QuestManager questManager
    ) {
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.debugManager = debugManager;
        this.questManager = questManager;
        this.blockedXpKey = new NamespacedKey(plugin, "warrior_blocked_xp");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(final CreatureSpawnEvent event) {
        if (!isBlockedSpawnReason(event.getSpawnReason())) {
            return;
        }

        event.getEntity().getPersistentDataContainer().set(blockedXpKey, PersistentDataType.STRING, event.getSpawnReason().name());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        final Player player = entity.getKiller();
        final Job job = Job.WARRIOR;

        if (player == null || !jobManager.isTrackedEntity(job, entity.getType())) {
            return;
        }

        final String blockedReason = getBlockedReason(entity);
        if (blockedReason != null) {
            debugManager.sendXpDebug(player.getName() + " erhielt keine Krieger-XP für "
                    + formatName(entity.getType().name()) + " wegen " + blockedReason + ".");
            return;
        }

        final int baseXp = jobManager.getConfiguredXpValue(job, entity.getType());
        if (baseXp <= 0) {
            return;
        }

        final AntiFarmResult antiFarmResult = applyRapidKillAntiFarm(player.getUniqueId(), entity.getLocation(), baseXp);
        if (antiFarmResult.adjustedXp() <= 0) {
            debugManager.sendXpDebug(player.getName() + " erhielt keine Krieger-XP für "
                    + formatName(entity.getType().name()) + ", weil die Anti-Farm-Multiplikation auf 0 fiel.");
            return;
        }

        final boolean doubleDrops = jobManager.shouldDoubleDrops(player.getUniqueId(), job);
        final Optional<BonusDropEntry> bonusDrop = jobManager.rollBonusDrop(player.getUniqueId(), job);

        final int granted = jobManager.grantExperience(player, job, antiFarmResult.adjustedXp());
        if (granted > 0) {
            questManager.recordObjective(player, job, QuestObjectiveType.KILL_ENTITY, entity.getType().name(), 1);
            final String suffix = antiFarmResult.reduced()
                    ? " (Anti-Farm aktiv, Basis " + baseXp + " -> " + antiFarmResult.adjustedXp() + ")"
                    : "";
            debugManager.sendXpDebug(player.getName() + " erhielt " + granted + " Krieger-XP für "
                    + formatName(entity.getType().name()) + suffix + ".");
        }
        applyPerkDrops(event, doubleDrops, bonusDrop);
    }

    private String getBlockedReason(final LivingEntity entity) {
        final String blockedSpawnReason = entity.getPersistentDataContainer().get(blockedXpKey, PersistentDataType.STRING);
        if (blockedSpawnReason != null) {
            return "Spawnreason " + blockedSpawnReason;
        }

        if (isBlockedEntityType(entity.getType().name())) {
            return "gesperrtem Entity-Typ";
        }

        if (configManager.getJobConfiguration(Job.WARRIOR).getBoolean("anti-farm.block-tamed", true)
                && entity instanceof Tameable tameable
                && tameable.isTamed()) {
            return "gezähmtem Mob";
        }

        if (configManager.getJobConfiguration(Job.WARRIOR).getBoolean("anti-farm.block-custom-named", true)
                && entity.customName() != null) {
            return "Custom-Namen";
        }

        if (configManager.getJobConfiguration(Job.WARRIOR).getBoolean("anti-farm.block-leashed", true)
                && entity instanceof Mob mob
                && mob.isLeashed()) {
            return "Leine";
        }

        return null;
    }

    private void applyPerkDrops(
            final EntityDeathEvent event,
            final boolean doubleDrops,
            final Optional<BonusDropEntry> bonusDrop
    ) {
        if (doubleDrops && !event.getDrops().isEmpty()) {
            final List<ItemStack> extraDrops = new ArrayList<>();
            for (final ItemStack drop : event.getDrops()) {
                extraDrops.add(drop.clone());
            }
            event.getDrops().addAll(extraDrops);
        }

        bonusDrop.ifPresent(entry -> event.getDrops().add(entry.toItemStack()));
    }

    private AntiFarmResult applyRapidKillAntiFarm(final UUID playerUuid, final Location location, final int baseXp) {
        final long windowMillis = Math.max(1L, configManager.getJobConfiguration(Job.WARRIOR).getLong("anti-farm.rapid-kill.window-seconds", 15L)) * 1000L;
        final double radius = Math.max(0.0D, configManager.getJobConfiguration(Job.WARRIOR).getDouble("anti-farm.rapid-kill.radius", 12.0D));
        final int threshold = Math.max(1, configManager.getJobConfiguration(Job.WARRIOR).getInt("anti-farm.rapid-kill.threshold", 10));
        final double multiplier = Math.max(0.0D, configManager.getJobConfiguration(Job.WARRIOR).getDouble("anti-farm.rapid-kill.multiplier", 0.5D));
        final long now = System.currentTimeMillis();

        final ArrayDeque<KillRecord> history = killHistory.computeIfAbsent(playerUuid, ignored -> new ArrayDeque<>());
        while (!history.isEmpty() && (now - history.peekFirst().timestamp()) > windowMillis) {
            history.pollFirst();
        }

        int nearbyKills = 1;
        final double maxDistanceSquared = radius * radius;
        for (final KillRecord record : history) {
            if (!record.worldUuid().equals(location.getWorld().getUID())) {
                continue;
            }
            if (record.location().distanceSquared(location) <= maxDistanceSquared) {
                nearbyKills++;
            }
        }

        history.addLast(new KillRecord(now, location.clone(), location.getWorld().getUID()));
        if (nearbyKills > threshold) {
            final int adjusted = Math.max(multiplier > 0.0D ? 1 : 0, (int) Math.floor(baseXp * multiplier));
            return new AntiFarmResult(adjusted, true);
        }

        return new AntiFarmResult(baseXp, false);
    }

    private boolean isBlockedSpawnReason(final CreatureSpawnEvent.SpawnReason spawnReason) {
        final List<String> blockedReasons = configManager.getJobConfiguration(Job.WARRIOR).getStringList("anti-farm.blocked-spawn-reasons");
        for (final String rawReason : blockedReasons) {
            if (spawnReason.name().equalsIgnoreCase(rawReason)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedEntityType(final String entityTypeName) {
        for (final String blockedType : configManager.getJobConfiguration(Job.WARRIOR).getStringList("anti-farm.blocked-entity-types")) {
            if (entityTypeName.equalsIgnoreCase(blockedType)) {
                return true;
            }
        }
        return false;
    }

    private String formatName(final String raw) {
        final String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private record KillRecord(long timestamp, Location location, UUID worldUuid) {
    }

    private record AntiFarmResult(int adjustedXp, boolean reduced) {
    }
}

