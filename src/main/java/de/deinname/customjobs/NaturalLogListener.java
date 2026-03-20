package de.deinname.customjobs;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class NaturalLogListener implements Listener {

    private final JobManager jobManager;
    private final ConfigManager configManager;
    private final NamespacedKey playerPlacedKey;
    private final Map<UUID, Deque<RecentLogBreak>> recentBreaks = new HashMap<>();
    private final Map<UUID, List<TreeComboSession>> comboSessions = new HashMap<>();

    
    public NaturalLogListener(
            final JavaPlugin plugin,
            final JobManager jobManager,
            final ConfigManager configManager
    ) {
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.playerPlacedKey = new NamespacedKey(plugin, "player_placed");
    }

    
    @EventHandler
    public void onBlockPlace(final BlockPlaceEvent event) {
        markAsPlayerPlaced(event.getBlockPlaced());
    }

    
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final Job job = Job.WOODCUTTER;

        if (!jobManager.isTrackedMaterial(job, block.getType())) {
            return;
        }

        if (isPlayerPlaced(block)) {
            unmarkPlayerPlaced(block);
            return;
        }

        int xp = jobManager.getConfiguredXpValue(job, block.getType());
        if (xp <= 0) {
            return;
        }

        if (shouldReduceExperience(player, block.getLocation())) {
            xp = Math.max(1, (int) Math.floor(xp * configManager.getAntiFarmMultiplier()));
        }

        final boolean doubleDrops = event.isDropItems() && jobManager.shouldDoubleDrops(player.getUniqueId(), job);
        final Optional<BonusDropEntry> bonusDrop = event.isDropItems()
                ? jobManager.rollBonusDrop(player.getUniqueId(), job)
                : Optional.empty();

        jobManager.grantExperience(player, job, xp);
        applyPerkDrops(event, block, player, doubleDrops, bonusDrop);
        handleTreeCombo(player, block, job);
    }

    
    private void markAsPlayerPlaced(final Block block) {
        final Chunk chunk = block.getChunk();
        final PersistentDataContainer container = chunk.getPersistentDataContainer();
        final Set<Long> keys = readPlacedBlockKeys(container);
        keys.add(block.getBlockKey());
        writePlacedBlockKeys(container, keys);
    }

    private boolean isPlayerPlaced(final Block block) {
        final PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        return readPlacedBlockKeys(container).contains(block.getBlockKey());
    }

    private void unmarkPlayerPlaced(final Block block) {
        final PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        final Set<Long> keys = readPlacedBlockKeys(container);
        if (!keys.remove(block.getBlockKey())) {
            return;
        }
        writePlacedBlockKeys(container, keys);
    }

    private Set<Long> readPlacedBlockKeys(final PersistentDataContainer container) {
        final long[] stored = container.get(playerPlacedKey, PersistentDataType.LONG_ARRAY);
        final Set<Long> keys = new HashSet<>();
        if (stored == null) {
            return keys;
        }

        for (final long value : stored) {
            keys.add(value);
        }
        return keys;
    }

    private void writePlacedBlockKeys(final PersistentDataContainer container, final Set<Long> keys) {
        if (keys.isEmpty()) {
            container.remove(playerPlacedKey);
            return;
        }

        final long[] values = new long[keys.size()];
        int index = 0;
        for (final long value : keys) {
            values[index++] = value;
        }
        container.set(playerPlacedKey, PersistentDataType.LONG_ARRAY, values);
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

    private void handleTreeCombo(final Player player, final Block block, final Job job) {
        final UUID playerUuid = player.getUniqueId();
        final long now = System.currentTimeMillis();
        cleanupExpiredSessions(playerUuid, now);

        final List<TreeComboSession> sessions = comboSessions.computeIfAbsent(playerUuid, ignored -> new ArrayList<>());
        TreeComboSession session = sessions.stream()
                .filter(existing -> existing.contains(block))
                .findFirst()
                .orElse(null);

        if (session == null) {
            session = createTreeComboSession(block, now);
            if (session == null) {
                return;
            }
            sessions.add(session);
        }

        session.markBroken(block);
        if (!session.isCompleted()) {
            return;
        }

        sessions.remove(session);
        if (sessions.isEmpty()) {
            comboSessions.remove(playerUuid);
        }

        final int comboXp = Math.max(1, session.totalLogs() * configManager.getComboBonusXpPerLog());
        jobManager.grantExperience(player, job, comboXp);
    }

    private TreeComboSession createTreeComboSession(final Block startBlock, final long now) {
        final Set<Long> logKeys = findConnectedNaturalLogs(startBlock);
        if (logKeys.size() < configManager.getComboMinimumLogs()) {
            return null;
        }

        if (requiresLeafCheck(startBlock.getType())
                && countNearbyCanopyBlocks(startBlock.getWorld(), logKeys) < configManager.getComboLeafRequirement()) {
            return null;
        }

        return new TreeComboSession(startBlock.getWorld().getUID(), logKeys, now);
    }

    private Set<Long> findConnectedNaturalLogs(final Block startBlock) {
        final Set<Long> visited = new HashSet<>();
        final Set<Long> naturalLogKeys = new HashSet<>();
        final Deque<Block> queue = new ArrayDeque<>();
        queue.add(startBlock);
        visited.add(startBlock.getBlockKey());

        while (!queue.isEmpty() && visited.size() <= configManager.getComboScanLimit()) {
            final Block current = queue.removeFirst();
            if (!jobManager.isTrackedMaterial(Job.WOODCUTTER, current.getType()) || isPlayerPlaced(current)) {
                continue;
            }

            naturalLogKeys.add(current.getBlockKey());

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }

                        final Block relative = current.getRelative(x, y, z);
                        final long relativeKey = relative.getBlockKey();
                        if (visited.add(relativeKey) && jobManager.isTrackedMaterial(Job.WOODCUTTER, relative.getType())) {
                            queue.addLast(relative);
                        }
                    }
                }
            }
        }

        return naturalLogKeys;
    }

    private int countNearbyCanopyBlocks(final World world, final Set<Long> logKeys) {
        final Set<Long> counted = new HashSet<>();
        int canopyBlocks = 0;

        for (final long logKey : logKeys) {
            final Block logBlock = world.getBlockAtKey(logKey);
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        final Block relative = logBlock.getRelative(x, y, z);
                        final long relativeKey = relative.getBlockKey();
                        if (!counted.add(relativeKey)) {
                            continue;
                        }

                        if (isCanopyMaterial(relative.getType())) {
                            canopyBlocks++;
                        }
                    }
                }
            }
        }

        return canopyBlocks;
    }

    private boolean isCanopyMaterial(final Material material) {
        return Tag.LEAVES.isTagged(material) || Tag.WART_BLOCKS.isTagged(material);
    }

    private boolean requiresLeafCheck(final Material material) {
        return material != Material.CRIMSON_STEM
                && material != Material.WARPED_STEM
                && material != Material.BAMBOO_BLOCK;
    }

    private void cleanupExpiredSessions(final UUID playerUuid, final long now) {
        final List<TreeComboSession> sessions = comboSessions.get(playerUuid);
        if (sessions == null) {
            return;
        }

        sessions.removeIf(session -> session.isExpired(now, configManager.getComboSessionMillis()));
        if (sessions.isEmpty()) {
            comboSessions.remove(playerUuid);
        }
    }

    private boolean shouldReduceExperience(final Player player, final Location location) {
        final UUID playerUuid = player.getUniqueId();
        final Deque<RecentLogBreak> breaks = recentBreaks.computeIfAbsent(playerUuid, ignored -> new ArrayDeque<>());
        final long now = System.currentTimeMillis();
        final long windowMillis = configManager.getAntiFarmWindowMillis();
        final double radiusSquared = Math.pow(configManager.getAntiFarmRadius(), 2);
        final int threshold = configManager.getAntiFarmThreshold();

        while (!breaks.isEmpty() && now - breaks.peekFirst().timestamp() > windowMillis) {
            breaks.removeFirst();
        }

        int nearbyCount = 1;
        for (final RecentLogBreak entry : breaks) {
            if (!entry.isSameWorld(location.getWorld())) {
                continue;
            }

            if (entry.distanceSquared(location) <= radiusSquared) {
                nearbyCount++;
            }
        }

        breaks.addLast(new RecentLogBreak(location, now));
        return nearbyCount > threshold;
    }

    
    private record RecentLogBreak(UUID worldId, double x, double y, double z, long timestamp) {

        private RecentLogBreak(final Location location, final long timestamp) {
            this(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(), timestamp);
        }

        private boolean isSameWorld(final World world) {
            return world != null && world.getUID().equals(worldId);
        }

        private double distanceSquared(final Location location) {
            final double dx = x - location.getX();
            final double dy = y - location.getY();
            final double dz = z - location.getZ();
            return (dx * dx) + (dy * dy) + (dz * dz);
        }
    }

    
    private static final class TreeComboSession {

        private final UUID worldId;
        private final Set<Long> logKeys;
        private final Set<Long> brokenKeys = new HashSet<>();
        private long lastActivity;

        private TreeComboSession(final UUID worldId, final Set<Long> logKeys, final long lastActivity) {
            this.worldId = worldId;
            this.logKeys = Set.copyOf(logKeys);
            this.lastActivity = lastActivity;
        }

        private boolean contains(final Block block) {
            return block.getWorld().getUID().equals(worldId) && logKeys.contains(block.getBlockKey());
        }

        private void markBroken(final Block block) {
            brokenKeys.add(block.getBlockKey());
            lastActivity = System.currentTimeMillis();
        }

        private boolean isCompleted() {
            return brokenKeys.size() >= logKeys.size();
        }

        private boolean isExpired(final long now, final long timeoutMillis) {
            return now - lastActivity > timeoutMillis;
        }

        private int totalLogs() {
            return logKeys.size();
        }
    }
}
