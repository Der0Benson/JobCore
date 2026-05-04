package de.derbenson.jobcore;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class PlayerDataManager implements Listener {

    private static final long SNAPSHOT_CACHE_MILLIS = 30_000L;

    private final JavaPlugin plugin;
    private final PlayerDataStorage storage;
    private final Map<UUID, PlayerJobData> playerData = new HashMap<>();
    private final ExecutorService storageExecutor;
    private volatile Map<UUID, PlayerJobData> cachedStoredSnapshot = Map.of();
    private volatile long cachedStoredSnapshotUntil;

    public PlayerDataManager(final JavaPlugin plugin, final PlayerDataStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.storageExecutor = Executors.newSingleThreadExecutor(new StorageThreadFactory());
    }

    public PlayerJobData loadPlayerData(final UUID playerUuid) {
        final PlayerJobData data = storage.load(playerUuid);
        synchronized (playerData) {
            playerData.put(playerUuid, data);
        }
        invalidateStoredSnapshotCache();
        return data;
    }

    public CompletableFuture<Map<UUID, PlayerJobData>> getAllPlayerDataSnapshotAsync() {
        return getAllPlayerDataSnapshotAsync(false);
    }

    public CompletableFuture<Map<UUID, PlayerJobData>> getAllPlayerDataSnapshotAsync(final boolean forceRefresh) {
        if (!forceRefresh && hasFreshStoredSnapshotCache()) {
            return CompletableFuture.completedFuture(mergeStoredAndLoadedSnapshots(copyStoredSnapshotCache()));
        }

        return CompletableFuture.supplyAsync(() -> {
            final Map<UUID, PlayerJobData> storedSnapshot = deepCopy(storage.loadAll());
            cachedStoredSnapshot = storedSnapshot;
            cachedStoredSnapshotUntil = System.currentTimeMillis() + SNAPSHOT_CACHE_MILLIS;
            return mergeStoredAndLoadedSnapshots(deepCopy(storedSnapshot));
        }, storageExecutor);
    }

    public Map<UUID, PlayerJobData> getAllPlayerDataSnapshotSync(final boolean forceRefresh) {
        if (!forceRefresh && hasFreshStoredSnapshotCache()) {
            return mergeStoredAndLoadedSnapshots(copyStoredSnapshotCache());
        }

        final Map<UUID, PlayerJobData> storedSnapshot = deepCopy(storage.loadAll());
        cachedStoredSnapshot = storedSnapshot;
        cachedStoredSnapshotUntil = System.currentTimeMillis() + SNAPSHOT_CACHE_MILLIS;
        return mergeStoredAndLoadedSnapshots(deepCopy(storedSnapshot));
    }

    public CompletableFuture<Optional<PlayerDataLookupResult>> findPlayerAsync(final String input) {
        return getAllPlayerDataSnapshotAsync(false).thenApply(snapshot -> findPlayerInSnapshot(snapshot, input));
    }

    public CompletableFuture<Void> savePlayerData(final UUID playerUuid) {
        final PlayerJobData dataSnapshot = getLoadedDataCopy(playerUuid);
        if (dataSnapshot == null) {
            return CompletableFuture.completedFuture(null);
        }

        return saveDetachedData(playerUuid, dataSnapshot);
    }

    public void savePlayerDataSync(final UUID playerUuid) {
        final PlayerJobData dataSnapshot = getLoadedDataCopy(playerUuid);
        if (dataSnapshot == null) {
            return;
        }

        storage.save(playerUuid, dataSnapshot);
        updateStoredSnapshotCache(playerUuid, dataSnapshot);
    }

    public CompletableFuture<Void> saveDetachedData(final UUID playerUuid, final PlayerJobData data) {
        final PlayerJobData dataSnapshot = data.copy();
        return CompletableFuture.runAsync(() -> {
            storage.save(playerUuid, dataSnapshot);
            updateStoredSnapshotCache(playerUuid, dataSnapshot);
        }, storageExecutor);
    }

    public CompletableFuture<Void> saveAll() {
        final Map<UUID, PlayerJobData> snapshot = getLoadedPlayerDataSnapshot();
        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            for (final Map.Entry<UUID, PlayerJobData> entry : snapshot.entrySet()) {
                storage.save(entry.getKey(), entry.getValue());
                updateStoredSnapshotCache(entry.getKey(), entry.getValue());
            }
        }, storageExecutor);
    }

    public void saveAllSync() {
        final Map<UUID, PlayerJobData> snapshot = getLoadedPlayerDataSnapshot();
        for (final Map.Entry<UUID, PlayerJobData> entry : snapshot.entrySet()) {
            storage.save(entry.getKey(), entry.getValue());
            updateStoredSnapshotCache(entry.getKey(), entry.getValue());
        }
    }

    public void unloadPlayerData(final UUID playerUuid) {
        savePlayerData(playerUuid).exceptionally(throwable -> {
            plugin.getLogger().severe("Spielerdaten konnten beim Verlassen nicht gespeichert werden: " + playerUuid);
            throwable.printStackTrace();
            return null;
        });
        synchronized (playerData) {
            playerData.remove(playerUuid);
        }
    }

    public PlayerJobData getOrCreateData(final UUID playerUuid) {
        synchronized (playerData) {
            return playerData.computeIfAbsent(playerUuid, storage::load);
        }
    }

    public PlayerJobData getLoadedData(final UUID playerUuid) {
        synchronized (playerData) {
            return playerData.get(playerUuid);
        }
    }

    public boolean isLoaded(final UUID playerUuid) {
        synchronized (playerData) {
            return playerData.containsKey(playerUuid);
        }
    }

    public PlayerJobData cacheDetachedData(final UUID playerUuid, final PlayerJobData data) {
        synchronized (playerData) {
            return playerData.computeIfAbsent(playerUuid, ignored -> data.copy());
        }
    }

    public void removeCachedData(final UUID playerUuid) {
        synchronized (playerData) {
            playerData.remove(playerUuid);
        }
    }

    public int cleanupLoadedData(final Predicate<PlayerJobData> cleanup) {
        int changed = 0;
        synchronized (playerData) {
            for (final PlayerJobData data : playerData.values()) {
                if (cleanup.test(data)) {
                    changed++;
                }
            }
        }

        if (changed > 0) {
            invalidateStoredSnapshotCache();
        }
        return changed;
    }

    public boolean isBossBarEnabled(final UUID playerUuid) {
        return getOrCreateData(playerUuid).isBossBarEnabled();
    }

    public void setBossBarEnabled(final UUID playerUuid, final boolean enabled) {
        getOrCreateData(playerUuid).setBossBarEnabled(enabled);
    }

    public boolean toggleBossBar(final UUID playerUuid) {
        final PlayerJobData data = getOrCreateData(playerUuid);
        final boolean enabled = !data.isBossBarEnabled();
        data.setBossBarEnabled(enabled);
        return enabled;
    }

    public void updateLastKnownName(final UUID playerUuid, final String playerName) {
        getOrCreateData(playerUuid).setLastKnownName(playerName);
    }

    public List<String> getKnownPlayerNames() {
        if (!hasFreshStoredSnapshotCache()) {
            getAllPlayerDataSnapshotAsync(false);
        }
        final Map<UUID, PlayerJobData> snapshot = mergeStoredAndLoadedSnapshots(copyStoredSnapshotCache());
        return snapshot.values().stream()
                .map(PlayerJobData::getLastKnownName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public void invalidateStoredSnapshotCache() {
        cachedStoredSnapshotUntil = 0L;
    }

    public void close() {
        storageExecutor.shutdown();
        try {
            if (!storageExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                storageExecutor.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            storageExecutor.shutdownNow();
        }
        storage.close();
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final PlayerJobData data = loadPlayerData(player.getUniqueId());
        data.setLastKnownName(player.getName());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        unloadPlayerData(event.getPlayer().getUniqueId());
    }

    private Optional<PlayerDataLookupResult> findPlayerInSnapshot(final Map<UUID, PlayerJobData> snapshot, final String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        final String trimmed = input.trim();
        try {
            final UUID playerUuid = UUID.fromString(trimmed);
            final PlayerJobData data = snapshot.get(playerUuid);
            if (data != null) {
                return Optional.of(new PlayerDataLookupResult(playerUuid, resolveName(playerUuid, data), data.copy()));
            }
        } catch (final IllegalArgumentException ignored) {
        }

        return snapshot.entrySet().stream()
                .filter(entry -> entry.getValue().getLastKnownName() != null && entry.getValue().getLastKnownName().equalsIgnoreCase(trimmed))
                .sorted(Comparator.comparing(entry -> entry.getValue().getLastKnownName(), String.CASE_INSENSITIVE_ORDER))
                .findFirst()
                .map(entry -> new PlayerDataLookupResult(entry.getKey(), resolveName(entry.getKey(), entry.getValue()), entry.getValue().copy()));
    }

    private String resolveName(final UUID playerUuid, final PlayerJobData data) {
        if (data.getLastKnownName() != null && !data.getLastKnownName().isBlank()) {
            return data.getLastKnownName();
        }
        final Player onlinePlayer = plugin.getServer().getPlayer(playerUuid);
        return onlinePlayer != null ? onlinePlayer.getName() : playerUuid.toString();
    }

    private PlayerJobData getLoadedDataCopy(final UUID playerUuid) {
        synchronized (playerData) {
            final PlayerJobData data = playerData.get(playerUuid);
            return data == null ? null : data.copy();
        }
    }

    private Map<UUID, PlayerJobData> getLoadedPlayerDataSnapshot() {
        synchronized (playerData) {
            return deepCopy(playerData);
        }
    }

    private boolean hasFreshStoredSnapshotCache() {
        return !cachedStoredSnapshot.isEmpty() && System.currentTimeMillis() <= cachedStoredSnapshotUntil;
    }

    private Map<UUID, PlayerJobData> copyStoredSnapshotCache() {
        return deepCopy(cachedStoredSnapshot);
    }

    private void updateStoredSnapshotCache(final UUID playerUuid, final PlayerJobData data) {
        final Map<UUID, PlayerJobData> updated = deepCopy(cachedStoredSnapshot);
        updated.put(playerUuid, data.copy());
        cachedStoredSnapshot = updated;
        cachedStoredSnapshotUntil = System.currentTimeMillis() + SNAPSHOT_CACHE_MILLIS;
    }

    private Map<UUID, PlayerJobData> mergeStoredAndLoadedSnapshots(final Map<UUID, PlayerJobData> storedSnapshot) {
        storedSnapshot.putAll(getLoadedPlayerDataSnapshot());
        return storedSnapshot;
    }

    private Map<UUID, PlayerJobData> deepCopy(final Map<UUID, PlayerJobData> source) {
        final Map<UUID, PlayerJobData> copy = new HashMap<>();
        for (final Map.Entry<UUID, PlayerJobData> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    public record PlayerDataLookupResult(UUID playerUuid, String playerName, PlayerJobData data) {
    }

    private static final class StorageThreadFactory implements ThreadFactory {

        private int counter = 1;

        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(runnable, "JobCore-Storage-" + counter++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
