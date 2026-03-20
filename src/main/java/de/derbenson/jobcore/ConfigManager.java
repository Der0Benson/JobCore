package de.derbenson.jobcore;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {

    private static final String DEFAULT_BOSSBAR_TEMPLATE =
            "<green>%job% <gray>- Lv.<white>%level% <gray>- <white>%xp%/%needed%</white> <gray>(+%xpGained% XP)</gray>";

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private final File jobDirectory;
    private FileConfiguration configuration;
    private final Map<Job, FileConfiguration> jobConfigurations = new EnumMap<>(Job.class);

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.jobDirectory = new File(plugin.getDataFolder(), "jobs");
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.configuration = plugin.getConfig();
        this.configuration.options().copyDefaults(true);
        plugin.saveConfig();
        reloadJobConfigurations();
    }

    public FileConfiguration getConfiguration() {
        return configuration;
    }

    public FileConfiguration getJobConfiguration(final Job job) {
        return jobConfigurations.getOrDefault(job, new YamlConfiguration());
    }

    public Component getMessage(final String path) {
        return getMessage(path, Map.of());
    }

    public Component getMessage(final String path, final Map<String, String> placeholders) {
        return getMessage(path, placeholders, "<red>Fehlende Nachricht: " + path + "</red>");
    }

    public Component getMessage(final String path, final Map<String, String> placeholders, final String fallback) {
        final String prefix = configuration.getString("messages.prefix", "");
        final String value = configuration.getString(path, fallback);
        return miniMessage.deserialize(applyPlaceholders(prefix + value, placeholders));
    }

    public Component deserialize(final String template) {
        return deserialize(template, Map.of());
    }

    public Component deserialize(final String template, final Map<String, String> placeholders) {
        return miniMessage.deserialize(applyPlaceholders(template, placeholders));
    }

    public String getStorageType() {
        return configuration.getString("storage.type", "yaml").trim().toLowerCase(Locale.ROOT);
    }

    public String getMySqlHost() {
        return configuration.getString("storage.mysql.host", "127.0.0.1");
    }

    public int getMySqlPort() {
        return Math.max(1, configuration.getInt("storage.mysql.port", 3306));
    }

    public String getMySqlDatabase() {
        return configuration.getString("storage.mysql.database", "jobcore");
    }

    public String getMySqlUsername() {
        return configuration.getString("storage.mysql.username", "root");
    }

    public String getMySqlPassword() {
        return configuration.getString("storage.mysql.password", "");
    }

    public String getMySqlTablePrefix() {
        return configuration.getString("storage.mysql.table-prefix", "jobcore_");
    }

    public boolean isMySqlUseSsl() {
        return configuration.getBoolean("storage.mysql.use-ssl", false);
    }

    public String getStorageSignature() {
        return String.join(
                "|",
                getStorageType(),
                getMySqlHost(),
                String.valueOf(getMySqlPort()),
                getMySqlDatabase(),
                getMySqlUsername(),
                getMySqlPassword(),
                getMySqlTablePrefix(),
                String.valueOf(isMySqlUseSsl())
        );
    }

    public String getBossBarTitleTemplate() {
        return configuration.getString("bossbar.title-template", DEFAULT_BOSSBAR_TEMPLATE);
    }

    public long getBossBarInactivityMillis() {
        return configuration.getLong("bossbar.inactivity-seconds", 10L) * 1000L;
    }

    public long getXpFormulaBase() {
        return Math.max(1L, configuration.getLong("xp-formula.base", 100L));
    }

    public long getXpFormulaPerLevel() {
        return Math.max(0L, configuration.getLong("xp-formula.per-level", 50L));
    }

    public long getAntiFarmWindowMillis() {
        return Math.max(1L, configuration.getLong("anti-farm.window-seconds", 5L)) * 1000L;
    }

    public double getAntiFarmRadius() {
        return Math.max(0.0D, configuration.getDouble("anti-farm.radius", 8.0D));
    }

    public int getAntiFarmThreshold() {
        return Math.max(1, configuration.getInt("anti-farm.threshold", 25));
    }

    public double getAntiFarmMultiplier() {
        return Math.max(0.0D, configuration.getDouble("anti-farm.multiplier", 0.5D));
    }

    public long getComboSessionMillis() {
        return Math.max(1L, configuration.getLong("combo.session-seconds", 20L)) * 1000L;
    }

    public int getComboMinimumLogs() {
        return Math.max(2, configuration.getInt("combo.minimum-logs", 4));
    }

    public int getComboBonusXpPerLog() {
        return Math.max(1, configuration.getInt("combo.bonus-xp-per-log", 2));
    }

    public int getComboScanLimit() {
        return Math.max(8, configuration.getInt("combo.scan-limit", 128));
    }

    public int getComboLeafRequirement() {
        return Math.max(0, configuration.getInt("combo.required-leaves", 6));
    }

    public String getLevelOverviewTitle() {
        return configuration.getString("menu.overview-title", "Job-Auswahl");
    }

    public String getLevelPathTitle() {
        return configuration.getString("menu.path-title", "Levelpfad");
    }

    public String getLevelMenuCloseLabel() {
        return configuration.getString("menu.close-label", "<red>SchlieÃŸen");
    }

    public String getLevelMenuBackLabel() {
        return configuration.getString("menu.back-label", "<yellow>Zur Ãœbersicht");
    }

    public String getLevelMenuPreviousPageLabel() {
        return configuration.getString("menu.previous-page-label", "<gold>Vorherige Seite");
    }

    public String getLevelMenuNextPageLabel() {
        return configuration.getString("menu.next-page-label", "<gold>NÃ¤chste Seite");
    }

    public String getJobDisplayName(final Job job) {
        return getJobConfiguration(job).getString("display-name", job.getDisplayName());
    }

    public Material getJobIcon(final Job job) {
        final String raw = getJobConfiguration(job).getString("icon", job.getIcon().name());
        final Material material = Material.matchMaterial(raw);
        if (material == null) {
            plugin.getLogger().warning("UngÃ¼ltiges Job-Icon fÃ¼r " + job.getId() + ": " + raw + ". Verwende " + job.getIcon().name() + ".");
            return job.getIcon();
        }
        return material;
    }

    public BossBar.Color getJobBossBarColor(final Job job) {
        final String raw = getJobConfiguration(job).getString("bossbar-color", job.getBarColor().name());
        try {
            return BossBar.Color.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            plugin.getLogger().warning("UngÃ¼ltige BossBar-Farbe fÃ¼r Job " + job.getId() + ": " + raw + ". Verwende Standardfarbe " + job.getBarColor().name() + ".");
            return job.getBarColor();
        }
    }

    private void reloadJobConfigurations() {
        jobConfigurations.clear();
        if (!jobDirectory.exists() && !jobDirectory.mkdirs()) {
            plugin.getLogger().warning("Job-Ordner konnte nicht erstellt werden: " + jobDirectory.getAbsolutePath());
        }

        for (final Job job : Job.values()) {
            final String resourcePath = "jobs/" + job.getId() + ".yml";
            final File jobFile = new File(jobDirectory, job.getId() + ".yml");
            if (!jobFile.exists() && plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            }

            if (!jobFile.exists()) {
                plugin.getLogger().warning("Job-Datei fehlt fÃ¼r " + job.getId() + ": " + jobFile.getAbsolutePath());
                jobConfigurations.put(job, new YamlConfiguration());
                continue;
            }

            final YamlConfiguration jobConfiguration = YamlConfiguration.loadConfiguration(jobFile);
            try (InputStream inputStream = plugin.getResource(resourcePath)) {
                if (inputStream != null) {
                    final YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                    );
                    jobConfiguration.setDefaults(defaults);
                    jobConfiguration.options().copyDefaults(true);
                    jobConfiguration.save(jobFile);
                }
            } catch (final Exception exception) {
                plugin.getLogger().warning("Job-Datei konnte nicht mit Defaults ergänzt werden für " + job.getId() + ": " + exception.getMessage());
            }

            jobConfigurations.put(job, jobConfiguration);
        }
    }

    private String applyPlaceholders(final String input, final Map<String, String> placeholders) {
        String result = input;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return result;
    }
}

