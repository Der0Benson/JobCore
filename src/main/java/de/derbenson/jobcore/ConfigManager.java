package de.derbenson.jobcore;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {

    private static final int MAIN_CONFIG_VERSION = 3;
    private static final int QUEST_CONFIG_VERSION = 3;
    private static final int JOB_CONFIG_VERSION = 2;
    private static final String DEFAULT_MESSAGE_PREFIX =
            "<bold><gradient:#EF3A45:#AE315E>ApexQuest ●</gradient></bold> ";
    private static final String DEFAULT_BOSSBAR_TEMPLATE =
            "<green>%job% <gray>- Lv.<white>%level% <gray>- <white>%xp%/%needed%</white> <gray>(+%xpGained% XP)</gray>";

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private final File jobDirectory;
    private final File questFile;
    private FileConfiguration configuration;
    private FileConfiguration questConfiguration;
    private final Map<Job, FileConfiguration> jobConfigurations = new EnumMap<>(Job.class);

    public ConfigManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.jobDirectory = new File(plugin.getDataFolder(), "jobs");
        this.questFile = new File(plugin.getDataFolder(), "quests.yml");
        plugin.saveDefaultConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.configuration = plugin.getConfig();
        configuration.options().copyDefaults(true);
        boolean changed = migrateManagedConfiguration(configuration, MAIN_CONFIG_VERSION);
        changed |= migrateMessagePrefix(configuration);
        if (changed) {
            plugin.saveConfig();
        }
        reloadJobConfigurations();
        reloadQuestConfiguration();
    }

    public FileConfiguration getConfiguration() {
        return configuration;
    }

    public FileConfiguration getJobConfiguration(final Job job) {
        return jobConfigurations.getOrDefault(job, new YamlConfiguration());
    }

    public FileConfiguration getQuestConfiguration() {
        return questConfiguration == null ? new YamlConfiguration() : questConfiguration;
    }

    public Component getMessage(final String path) {
        return getMessage(path, Map.of());
    }

    public Component getMessage(final String path, final Map<String, String> placeholders) {
        return getMessage(path, placeholders, "<red>Fehlende Nachricht: " + path + "</red>");
    }

    public Component getMessage(final String path, final Map<String, String> placeholders, final String fallback) {
        final String value = configuration.getString(path, fallback);
        return miniMessage.deserialize(applyPlaceholders(getMessagePrefix() + value, placeholders));
    }

    public Component getChatMessage(final String template) {
        return getChatMessage(template, Map.of());
    }

    public Component getChatMessage(final String template, final Map<String, String> placeholders) {
        return miniMessage.deserialize(applyPlaceholders(getMessagePrefix() + template, placeholders));
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

    public long getLeaderboardCacheMillis() {
        return Math.max(5L, configuration.getLong("leaderboards.cache-seconds", 30L)) * 1000L;
    }

    public String getLevelOverviewTitle() {
        return configuration.getString("menu.overview-title", "Job-Auswahl");
    }

    public String getLevelPathTitle() {
        return configuration.getString("menu.path-title", "Levelpfad");
    }

    public String getLevelMenuCloseLabel() {
        return configuration.getString("menu.close-label", "<red>Schließen");
    }

    public String getLevelMenuBackLabel() {
        return configuration.getString("menu.back-label", "<yellow>Zur Übersicht");
    }

    public String getLevelMenuPreviousPageLabel() {
        return configuration.getString("menu.previous-page-label", "<gold>Vorherige Seite");
    }

    public String getLevelMenuNextPageLabel() {
        return configuration.getString("menu.next-page-label", "<gold>Nächste Seite");
    }

    public String getJobDisplayName(final Job job) {
        return getJobConfiguration(job).getString("display-name", job.getDisplayName());
    }

    public Material getJobIcon(final Job job) {
        final String raw = getJobConfiguration(job).getString("icon", job.getIcon().name());
        final Material material = Material.matchMaterial(raw);
        if (material == null) {
            plugin.getLogger().warning("Ungültiges Job-Icon für " + job.getId() + ": " + raw + ". Verwende " + job.getIcon().name() + ".");
            return job.getIcon();
        }
        return material;
    }

    public BossBar.Color getJobBossBarColor(final Job job) {
        final String raw = getJobConfiguration(job).getString("bossbar-color", job.getBarColor().name());
        try {
            return BossBar.Color.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            plugin.getLogger().warning("Ungültige BossBar-Farbe für Job " + job.getId() + ": " + raw + ". Verwende Standardfarbe " + job.getBarColor().name() + ".");
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
                plugin.getLogger().warning("Job-Datei fehlt für " + job.getId() + ": " + jobFile.getAbsolutePath());
                jobConfigurations.put(job, new YamlConfiguration());
                continue;
            }

            final YamlConfiguration jobConfiguration = YamlConfiguration.loadConfiguration(jobFile);
            boolean changed = false;
            try (InputStream inputStream = plugin.getResource(resourcePath)) {
                if (inputStream != null) {
                    final YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                    );
                    jobConfiguration.setDefaults(defaults);
                    jobConfiguration.options().copyDefaults(true);
                    changed = true;
                }
            } catch (final Exception exception) {
                plugin.getLogger().warning("Job-Datei konnte nicht mit Defaults ergänzt werden für " + job.getId() + ": " + exception.getMessage());
            }

            changed |= migrateManagedConfiguration(jobConfiguration, JOB_CONFIG_VERSION);
            if (changed) {
                saveYaml(jobConfiguration, jobFile);
            }

            jobConfigurations.put(job, jobConfiguration);
        }
    }

    private void reloadQuestConfiguration() {
        if (!questFile.exists() && plugin.getResource("quests.yml") != null) {
            plugin.saveResource("quests.yml", false);
        }

        if (!questFile.exists()) {
            questConfiguration = new YamlConfiguration();
            return;
        }

        final YamlConfiguration loadedConfiguration = YamlConfiguration.loadConfiguration(questFile);
        boolean changed = false;
        try (InputStream inputStream = plugin.getResource("quests.yml")) {
            if (inputStream != null) {
                final YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                );
                loadedConfiguration.setDefaults(defaults);
                loadedConfiguration.options().copyDefaults(true);
                changed = true;
            }
        } catch (final Exception exception) {
            plugin.getLogger().warning("Quest-Datei konnte nicht mit Defaults ergänzt werden: " + exception.getMessage());
        }

        changed |= migrateManagedConfiguration(loadedConfiguration, QUEST_CONFIG_VERSION);
        if (changed) {
            saveYaml(loadedConfiguration, questFile);
        }

        questConfiguration = loadedConfiguration;
    }

    private boolean migrateManagedConfiguration(final ConfigurationSection configurationSection, final int latestVersion) {
        boolean changed = repairConfigurationSection(configurationSection);
        if (configurationSection.getInt("config-version", 0) < latestVersion) {
            configurationSection.set("config-version", latestVersion);
            changed = true;
        }
        return changed;
    }

    private boolean migrateMessagePrefix(final ConfigurationSection configurationSection) {
        final String prefix = configurationSection.getString("messages.prefix", "");
        if (!isBuiltInDefaultPrefix(prefix)) {
            return false;
        }

        configurationSection.set("messages.prefix", DEFAULT_MESSAGE_PREFIX);
        return true;
    }

    private String getMessagePrefix() {
        final String prefix = configuration.getString("messages.prefix", DEFAULT_MESSAGE_PREFIX);
        return isBuiltInDefaultPrefix(prefix) ? DEFAULT_MESSAGE_PREFIX : prefix;
    }

    private boolean isBuiltInDefaultPrefix(final String prefix) {
        return prefix == null
                || prefix.isBlank()
                || prefix.equals("<gray>[<green>JobCore<gray>] ")
                || prefix.equals("<gray>[<green>JobCore</green><gray>] ");
    }

    private boolean repairConfigurationSection(final ConfigurationSection configurationSection) {
        boolean changed = false;
        for (final String key : configurationSection.getKeys(false)) {
            final Object value = configurationSection.get(key);
            if (value instanceof ConfigurationSection childSection) {
                changed |= repairConfigurationSection(childSection);
                continue;
            }

            if (value instanceof String stringValue) {
                final String fallback = getDefaultString(configurationSection, key);
                final String repaired = repairMojibake(stringValue, fallback);
                if (!repaired.equals(stringValue)) {
                    configurationSection.set(key, repaired);
                    changed = true;
                }
                continue;
            }

            if (value instanceof List<?> listValue) {
                final RepairResult repairResult = repairList(listValue);
                final List<?> repairedValues = (List<?>) repairResult.value();
                if (repairResult.changed()) {
                    configurationSection.set(key, repairedValues);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private RepairResult repairList(final List<?> listValue) {
        final List<Object> repairedValues = new ArrayList<>(listValue.size());
        boolean listChanged = false;
        for (final Object entry : listValue) {
            final RepairResult repairResult = repairObject(entry);
            repairedValues.add(repairResult.value());
            listChanged |= repairResult.changed();
        }
        return new RepairResult(repairedValues, listChanged);
    }

    private RepairResult repairMap(final Map<?, ?> mapValue) {
        final Map<Object, Object> repairedValues = new LinkedHashMap<>();
        boolean changed = false;
        for (final Map.Entry<?, ?> entry : mapValue.entrySet()) {
            final RepairResult repairResult = repairObject(entry.getValue());
            repairedValues.put(entry.getKey(), repairResult.value());
            changed |= repairResult.changed();
        }
        return new RepairResult(repairedValues, changed);
    }

    private RepairResult repairObject(final Object value) {
        if (value instanceof String stringValue) {
            final String repaired = repairMojibake(stringValue, null);
            return new RepairResult(repaired, !repaired.equals(stringValue));
        }
        if (value instanceof List<?> listValue) {
            return repairList(listValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return repairMap(mapValue);
        }
        return new RepairResult(value, false);
    }

    private String repairMojibake(final String input, final String fallback) {
        if (input == null || input.isBlank()) {
            return input;
        }

        if (input.contains("\uFFFD") && fallback != null && !fallback.contains("\uFFFD")) {
            return fallback;
        }

        if (!looksLikeMojibake(input)) {
            return input;
        }

        final String repaired = new String(input.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return qualityScore(repaired) > qualityScore(input) ? repaired : input;
    }

    private boolean looksLikeMojibake(final String input) {
        return input.contains("\uFFFD")
                || input.contains("Ã")
                || input.contains("Â")
                || input.contains("â");
    }

    private int qualityScore(final String input) {
        int score = 0;
        score += countOccurrences(input, "ä") * 4;
        score += countOccurrences(input, "ö") * 4;
        score += countOccurrences(input, "ü") * 4;
        score += countOccurrences(input, "Ä") * 4;
        score += countOccurrences(input, "Ö") * 4;
        score += countOccurrences(input, "Ü") * 4;
        score += countOccurrences(input, "ß") * 4;
        score += countOccurrences(input, "§") * 2;
        score -= countOccurrences(input, "\uFFFD") * 8;
        score -= countOccurrences(input, "Ã") * 4;
        score -= countOccurrences(input, "Â") * 4;
        score -= countOccurrences(input, "â") * 3;
        return score;
    }

    private String getDefaultString(final ConfigurationSection configurationSection, final String key) {
        if (configurationSection.getRoot() == null || configurationSection.getRoot().getDefaults() == null) {
            return null;
        }

        final String currentPath = configurationSection.getCurrentPath();
        final String fullPath = currentPath == null || currentPath.isBlank() ? key : currentPath + "." + key;
        if (!configurationSection.getRoot().getDefaults().isString(fullPath)) {
            return null;
        }

        return configurationSection.getRoot().getDefaults().getString(fullPath);
    }

    private int countOccurrences(final String input, final String token) {
        int count = 0;
        int index = 0;
        while ((index = input.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private void saveYaml(final YamlConfiguration configuration, final File file) {
        try {
            configuration.save(file);
        } catch (final Exception exception) {
            plugin.getLogger().warning("Datei konnte nicht gespeichert werden: " + file.getAbsolutePath() + " - " + exception.getMessage());
        }
    }

    private String applyPlaceholders(final String input, final Map<String, String> placeholders) {
        String result = input;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return result;
    }

    private record RepairResult(Object value, boolean changed) {
    }
}
