package de.derbenson.jobcore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class ExportManager {

    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final QuestManager questManager;

    public ExportManager(
            final JavaPlugin plugin,
            final ConfigManager configManager,
            final PlayerDataManager playerDataManager,
            final QuestManager questManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.questManager = questManager;
    }

    public ExportResult exportSnapshot() throws IOException {
        playerDataManager.saveAllSync();

        final File exportDirectory = new File(plugin.getDataFolder(), "exports");
        if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
            throw new IOException("Export-Ordner konnte nicht erstellt werden: " + exportDirectory.getAbsolutePath());
        }

        final Map<UUID, PlayerJobData> snapshot = new TreeMap<>(playerDataManager.getAllPlayerDataSnapshotSync(true));
        final Map<String, Quest> knownQuests = new LinkedHashMap<>();
        for (final Quest quest : questManager.getConfiguredQuests()) {
            knownQuests.put(quest.id(), quest);
        }

        final YamlConfiguration export = new YamlConfiguration();
        export.set("generated-at", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        export.set("storage-type", configManager.getStorageType());
        export.set("player-count", snapshot.size());
        export.set("jobs", java.util.Arrays.stream(Job.values()).map(Job::getId).toList());
        export.set("quests", knownQuests.keySet().stream().toList());

        final ConfigurationSection playersSection = export.createSection("players");
        for (final Map.Entry<UUID, PlayerJobData> entry : snapshot.entrySet()) {
            final UUID playerUuid = entry.getKey();
            final PlayerJobData data = entry.getValue();
            final ConfigurationSection playerSection = playersSection.createSection(playerUuid.toString());
            playerSection.set("name", data.getLastKnownName());
            playerSection.set("settings.bossbar-enabled", data.isBossBarEnabled());
            playerSection.set("settings.quest-claims-total", data.getTotalQuestClaims());

            final ConfigurationSection jobsSection = playerSection.createSection("jobs");
            for (final Job job : Job.values()) {
                final JobProgress progress = data.getProgressByJob().get(job.getId());
                final ConfigurationSection jobSection = jobsSection.createSection(job.getId());
                jobSection.set("display-name", configManager.getJobDisplayName(job));
                jobSection.set("level", progress == null ? 0 : progress.getLevel());
                jobSection.set("xp", progress == null ? 0L : progress.getXp());
                jobSection.set("fractional-xp", progress == null ? 0.0D : progress.getFractionalXp());
            }

            final LinkedHashSet<String> questIds = new LinkedHashSet<>(knownQuests.keySet());
            data.getQuestProgressById().keySet().stream()
                    .sorted()
                    .forEach(questIds::add);

            final ConfigurationSection questsSection = playerSection.createSection("quests");
            for (final String questId : questIds) {
                final PlayerQuestProgress progress = data.getQuestProgressById().get(questId);
                final Quest quest = knownQuests.get(questId);
                final ConfigurationSection questSection = questsSection.createSection(questId);
                if (quest != null) {
                    questSection.set("display-name", quest.displayName());
                    questSection.set("period", quest.period().getId());
                    questSection.set("job", quest.job().getId());
                }
                questSection.set("progress", progress == null ? 0 : progress.getProgress());
                questSection.set("accepted", progress != null && progress.isAccepted());
                questSection.set("completed", progress != null && progress.isCompleted());
                questSection.set("claimed", progress != null && progress.isClaimed());
                questSection.set("cycle-key", progress == null ? "" : progress.getCycleKey());
            }
        }

        final File exportFile = new File(
                exportDirectory,
                "export-" + FILE_TIMESTAMP_FORMAT.format(ZonedDateTime.now()) + ".yml"
        );
        export.save(exportFile);
        return new ExportResult(exportFile, snapshot.size());
    }

    public record ExportResult(File file, int playerCount) {
    }
}
