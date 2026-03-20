package de.deinname.customjobs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PlayerDataManager implements Listener {

    private final JavaPlugin plugin;
    private final String legacyDefaultJobId;
    private final File playerDataFolder;
    private final Map<UUID, PlayerJobData> playerData = new HashMap<>();

    
    public PlayerDataManager(final JavaPlugin plugin, final String legacyDefaultJobId) {
        this.plugin = plugin;
        this.legacyDefaultJobId = legacyDefaultJobId.toLowerCase(Locale.ROOT);
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");

        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            plugin.getLogger().warning("Ordner fuer Spielerdaten konnte nicht erstellt werden: "
                    + playerDataFolder.getAbsolutePath());
        }
    }

    
    public PlayerJobData loadPlayerData(final UUID playerUuid) {
        final File file = getPlayerDataFile(playerUuid);
        if (!file.exists()) {
            final PlayerJobData newData = createDefaultData();
            playerData.put(playerUuid, newData);
            return newData;
        }

        try {
            final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            final PlayerJobData data = new PlayerJobData();
            final ConfigurationSection jobsSection = configuration.getConfigurationSection("jobs");

            if (jobsSection != null) {
                for (final String jobId : jobsSection.getKeys(false)) {
                    final ConfigurationSection jobSection = jobsSection.getConfigurationSection(jobId);
                    if (jobSection == null) {
                        continue;
                    }

                    final long xp = Math.max(0L, jobSection.getLong("xp", 0L));
                    final int level = Math.max(0, jobSection.getInt("level", 0));
                    final double fractionalXp = Math.max(0.0D, jobSection.getDouble("fractional-xp", 0.0D));
                    data.setProgress(normalizeJobId(jobId), new JobProgress(xp, level, fractionalXp));
                }
            } else {
                final String legacyJobId = normalizeJobId(configuration.getString("current-job", legacyDefaultJobId));
                final long legacyXp = Math.max(0L, configuration.getLong("xp", 0L));
                final int legacyLevel = Math.max(0, configuration.getInt("level", 0));
                data.setProgress(legacyJobId, new JobProgress(legacyXp, legacyLevel, 0.0D));
            }

            playerData.put(playerUuid, data);
            return data;
        } catch (final Exception exception) {
            plugin.getLogger().severe("Fehler beim Laden der Daten fuer " + playerUuid + ": " + exception.getMessage());
            final PlayerJobData fallbackData = createDefaultData();
            playerData.put(playerUuid, fallbackData);
            return fallbackData;
        }
    }

    
    public void savePlayerData(final UUID playerUuid) {
        final PlayerJobData data = playerData.get(playerUuid);
        if (data == null) {
            return;
        }

        final File file = getPlayerDataFile(playerUuid);
        final YamlConfiguration configuration = new YamlConfiguration();

        for (final Map.Entry<String, JobProgress> entry : data.getProgressByJob().entrySet()) {
            final JobProgress progress = entry.getValue();
            final String basePath = "jobs." + normalizeJobId(entry.getKey());
            configuration.set(basePath + ".xp", Math.max(0L, progress.getXp()));
            configuration.set(basePath + ".level", Math.max(0, progress.getLevel()));
            configuration.set(basePath + ".fractional-xp", Math.max(0.0D, progress.getFractionalXp()));
        }

        try {
            configuration.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Fehler beim Speichern der Daten fuer " + playerUuid + ": " + exception.getMessage());
        }
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
        return playerData.computeIfAbsent(playerUuid, ignored -> createDefaultData());
    }

    
    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer().getUniqueId());
    }

    
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        unloadPlayerData(event.getPlayer().getUniqueId());
    }

    private PlayerJobData createDefaultData() {
        return new PlayerJobData();
    }

    private File getPlayerDataFile(final UUID playerUuid) {
        return new File(playerDataFolder, playerUuid + ".yml");
    }

    private String normalizeJobId(final String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return legacyDefaultJobId;
        }
        return jobId.toLowerCase(Locale.ROOT);
    }
}
