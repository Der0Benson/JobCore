package de.deinname.customjobs;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerDataManager implements Listener {

    private final PlayerDataStorage storage;
    private final Map<UUID, PlayerJobData> playerData = new HashMap<>();

    public PlayerDataManager(final PlayerDataStorage storage) {
        this.storage = storage;
    }

    public PlayerJobData loadPlayerData(final UUID playerUuid) {
        final PlayerJobData data = storage.load(playerUuid);
        playerData.put(playerUuid, data);
        return data;
    }

    public void savePlayerData(final UUID playerUuid) {
        final PlayerJobData data = playerData.get(playerUuid);
        if (data == null) {
            return;
        }
        storage.save(playerUuid, data);
    }

    public void saveAll() {
        for (final UUID playerUuid : new ArrayList<>(playerData.keySet())) {
            savePlayerData(playerUuid);
        }
    }

    public void unloadPlayerData(final UUID playerUuid) {
        savePlayerData(playerUuid);
        playerData.remove(playerUuid);
    }

    public PlayerJobData getOrCreateData(final UUID playerUuid) {
        return playerData.computeIfAbsent(playerUuid, storage::load);
    }

    public void close() {
        storage.close();
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        unloadPlayerData(event.getPlayer().getUniqueId());
    }
}
