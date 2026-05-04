package de.derbenson.jobcore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class YamlPlayerDataStorage implements PlayerDataStorage {

    private final JavaPlugin plugin;
    private final String legacyDefaultJobId;
    private final File playerDataFolder;

    public YamlPlayerDataStorage(final JavaPlugin plugin, final String legacyDefaultJobId) {
        this.plugin = plugin;
        this.legacyDefaultJobId = legacyDefaultJobId.toLowerCase(Locale.ROOT);
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
    }

    @Override
    public void initialize() {
        if (!playerDataFolder.exists() && !playerDataFolder.mkdirs()) {
            plugin.getLogger().warning("Ordner für Spielerdaten konnte nicht erstellt werden: "
                    + playerDataFolder.getAbsolutePath());
        }
    }

    @Override
    public PlayerJobData load(final UUID playerUuid) {
        final File file = getPlayerDataFile(playerUuid);
        if (!file.exists()) {
            return new PlayerJobData();
        }

        try {
            return loadFromConfiguration(YamlConfiguration.loadConfiguration(file));
        } catch (final Exception exception) {
            plugin.getLogger().severe("Fehler beim Laden der Daten für " + playerUuid + ": " + exception.getMessage());
            return new PlayerJobData();
        }
    }

    @Override
    public Map<UUID, PlayerJobData> loadAll() {
        final Map<UUID, PlayerJobData> entries = new HashMap<>();
        if (!playerDataFolder.exists()) {
            return entries;
        }

        final File[] files = playerDataFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return entries;
        }

        for (final File file : files) {
            final String fileName = file.getName();
            final String uuidPart = fileName.substring(0, fileName.length() - 4);
            try {
                final UUID playerUuid = UUID.fromString(uuidPart);
                entries.put(playerUuid, loadFromConfiguration(YamlConfiguration.loadConfiguration(file)));
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("Ungültige Spielerdatei übersprungen: " + fileName);
            } catch (final Exception exception) {
                plugin.getLogger().warning("Spielerdaten konnten nicht geladen werden: " + fileName + " - " + exception.getMessage());
            }
        }

        return entries;
    }

    @Override
    public void save(final UUID playerUuid, final PlayerJobData data) {
        final File file = getPlayerDataFile(playerUuid);
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("settings.bossbar-enabled", data.isBossBarEnabled());
        configuration.set("settings.player-name", data.getLastKnownName());
        configuration.set("settings.quest-claims-total", data.getTotalQuestClaims());

        for (final Map.Entry<String, JobProgress> entry : data.getProgressByJob().entrySet()) {
            final JobProgress progress = entry.getValue();
            final String basePath = "jobs." + normalizeJobId(entry.getKey());
            configuration.set(basePath + ".xp", Math.max(0L, progress.getXp()));
            configuration.set(basePath + ".level", Math.max(0, progress.getLevel()));
            configuration.set(basePath + ".fractional-xp", Math.max(0.0D, progress.getFractionalXp()));
        }

        for (final Map.Entry<String, PlayerQuestProgress> entry : data.getQuestProgressById().entrySet()) {
            final PlayerQuestProgress progress = entry.getValue();
            final String basePath = "quests." + entry.getKey().toLowerCase(Locale.ROOT);
            configuration.set(basePath + ".progress", Math.max(0, progress.getProgress()));
            configuration.set(basePath + ".accepted", progress.isAccepted());
            configuration.set(basePath + ".completed", progress.isCompleted());
            configuration.set(basePath + ".claimed", progress.isClaimed());
            configuration.set(basePath + ".cycle-key", progress.getCycleKey());
        }

        try {
            configuration.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Fehler beim Speichern der Daten für " + playerUuid + ": " + exception.getMessage());
        }
    }

    @Override
    public void close() {
    }

    private File getPlayerDataFile(final UUID playerUuid) {
        return new File(playerDataFolder, playerUuid + ".yml");
    }

    private PlayerJobData loadFromConfiguration(final YamlConfiguration configuration) {
        final PlayerJobData data = new PlayerJobData();
        data.setBossBarEnabled(configuration.getBoolean("settings.bossbar-enabled", true));
        data.setLastKnownName(configuration.getString("settings.player-name", ""));
        data.setTotalQuestClaims(Math.max(0, configuration.getInt("settings.quest-claims-total", 0)));
        final ConfigurationSection jobsSection = configuration.getConfigurationSection("jobs");
        final ConfigurationSection questsSection = configuration.getConfigurationSection("quests");

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

        if (questsSection != null) {
            for (final String questId : questsSection.getKeys(false)) {
                final ConfigurationSection questSection = questsSection.getConfigurationSection(questId);
                if (questSection == null) {
                    continue;
                }

                final int progress = Math.max(0, questSection.getInt("progress", 0));
                final boolean completed = questSection.getBoolean("completed", false);
                final boolean accepted = questSection.getBoolean("accepted", progress > 0 && !completed);
                final boolean claimed = questSection.getBoolean("claimed", false);
                final String cycleKey = questSection.getString("cycle-key", "");
                data.setQuestProgress(
                        questId.toLowerCase(Locale.ROOT),
                        new PlayerQuestProgress(progress, accepted, completed, claimed, cycleKey)
                );
            }
        }

        return data;
    }

    private String normalizeJobId(final String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return legacyDefaultJobId;
        }
        return jobId.toLowerCase(Locale.ROOT);
    }
}
