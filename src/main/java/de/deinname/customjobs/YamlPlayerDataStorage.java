package de.deinname.customjobs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
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
            plugin.getLogger().warning("Ordner fuer Spielerdaten konnte nicht erstellt werden: "
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

            return data;
        } catch (final Exception exception) {
            plugin.getLogger().severe("Fehler beim Laden der Daten fuer " + playerUuid + ": " + exception.getMessage());
            return new PlayerJobData();
        }
    }

    @Override
    public void save(final UUID playerUuid, final PlayerJobData data) {
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

    @Override
    public void close() {
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
