package de.derbenson.jobcore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class QuestManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final JobManager jobManager;
    private final QuestFeedbackManager questFeedbackManager;
    private final ZoneId zoneId;
    private final Map<String, Quest> configuredQuestsById = new LinkedHashMap<>();
    private final Map<String, Quest> activeQuestsById = new LinkedHashMap<>();
    private final Map<QuestPeriod, Quest> activeQuestsByPeriod = new EnumMap<>(QuestPeriod.class);

    public QuestManager(
            final JavaPlugin plugin,
            final ConfigManager configManager,
            final PlayerDataManager playerDataManager,
            final JobManager jobManager,
            final QuestFeedbackManager questFeedbackManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.jobManager = jobManager;
        this.questFeedbackManager = questFeedbackManager;
        this.zoneId = ZoneId.systemDefault();
        reload();
    }

    public void reload() {
        configuredQuestsById.clear();
        activeQuestsById.clear();
        activeQuestsByPeriod.clear();

        final FileConfiguration configuration = configManager.getQuestConfiguration();
        final ConfigurationSection missionsSection = configuration.getConfigurationSection("missions");
        if (missionsSection == null) {
            plugin.getLogger().warning("In quests.yml fehlt der Bereich 'missions'.");
            return;
        }

        for (final QuestPeriod period : QuestPeriod.values()) {
            final ConfigurationSection missionSection = missionsSection.getConfigurationSection(period.getId());
            if (missionSection == null) {
                plugin.getLogger().warning("Mission '" + period.getId() + "' fehlt in quests.yml.");
                continue;
            }

            final List<Quest> candidates = parseQuestCandidates(period, missionSection);
            if (candidates.isEmpty()) {
                plugin.getLogger().warning("Für '" + period.getId() + "' wurden keine gültigen Missions-Kandidaten gefunden.");
                continue;
            }

            for (final Quest quest : candidates) {
                configuredQuestsById.put(quest.id(), quest);
            }

            final Quest activeQuest = selectActiveQuest(period, candidates);
            activeQuestsByPeriod.put(period, activeQuest);
            activeQuestsById.put(activeQuest.id(), activeQuest);
        }
    }

    public List<Quest> getQuests() {
        return List.copyOf(activeQuestsById.values());
    }

    public List<Quest> getConfiguredQuests() {
        return List.copyOf(configuredQuestsById.values());
    }

    public Optional<Quest> getQuest(final String questId) {
        if (questId == null || questId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeQuestsById.get(questId.toLowerCase(Locale.ROOT)));
    }

    public PlayerQuestProgress getProgress(final UUID playerUuid, final String questId) {
        final Optional<Quest> quest = getQuest(questId);
        if (quest.isEmpty()) {
            return null;
        }
        final PlayerJobData data = playerDataManager.getOrCreateData(playerUuid);
        return getCurrentProgress(data, quest.get(), true);
    }

    public int getActiveQuestCount(final UUID playerUuid) {
        final PlayerJobData data = playerDataManager.getOrCreateData(playerUuid);
        int count = 0;
        for (final Quest quest : getQuests()) {
            final PlayerQuestProgress progress = getCurrentProgress(data, quest, true);
            if (progress.isAccepted() && !progress.isCompleted() && !progress.isClaimed()) {
                count++;
            }
        }
        return count;
    }

    public int getClaimableQuestCount(final UUID playerUuid) {
        final PlayerJobData data = playerDataManager.getOrCreateData(playerUuid);
        int count = 0;
        for (final Quest quest : getQuests()) {
            final PlayerQuestProgress progress = getCurrentProgress(data, quest, true);
            if (progress.isCompleted() && !progress.isClaimed()) {
                count++;
            }
        }
        return count;
    }

    public int getClaimedQuestCount(final UUID playerUuid) {
        final PlayerJobData data = playerDataManager.getOrCreateData(playerUuid);
        int count = 0;
        for (final Quest quest : getQuests()) {
            final PlayerQuestProgress progress = getCurrentProgress(data, quest, true);
            if (progress.isClaimed()) {
                count++;
            }
        }
        return count;
    }

    public boolean acceptQuest(final Player player, final String questId) {
        final Optional<Quest> quest = getQuest(questId);
        if (quest.isEmpty()) {
            return false;
        }

        final PlayerJobData data = playerDataManager.getOrCreateData(player.getUniqueId());
        final PlayerQuestProgress progress = getCurrentProgress(data, quest.get(), true);

        if (progress.isClaimed()) {
            player.sendMessage(configManager.getMessage(
                    "messages.quest-cycle-finished",
                    Map.of("quest", quest.get().displayName(), "period", quest.get().period().getDisplayName()),
                    "<yellow>Diese %period%-Mission <white>%quest%</white><yellow> ist in diesem Zyklus bereits erledigt.</yellow>"
            ));
            return false;
        }

        if (progress.isCompleted()) {
            return claimQuest(player, questId);
        }

        if (progress.isAccepted()) {
            player.sendMessage(configManager.getMessage(
                    "messages.quest-already-active",
                    Map.of("quest", quest.get().displayName()),
                    "<yellow>Die Mission <white>%quest%</white><yellow> ist bereits aktiv.</yellow>"
            ));
            return false;
        }

        progress.setProgress(0);
        progress.setAccepted(true);
        progress.setCompleted(false);
        progress.setClaimed(false);
        progress.setCycleKey(currentCycleKey(quest.get().period()));
        playerDataManager.savePlayerData(player.getUniqueId());

        player.sendMessage(configManager.getMessage(
                "messages.quest-accepted",
                Map.of("quest", quest.get().displayName(), "period", quest.get().period().getDisplayName()),
                "<green>Mission angenommen: <white>%quest%</white>"
        ));
        return true;
    }

    public boolean abandonQuest(final Player player, final String questId) {
        final Optional<Quest> quest = getQuest(questId);
        if (quest.isEmpty()) {
            return false;
        }

        final PlayerJobData data = playerDataManager.getOrCreateData(player.getUniqueId());
        final PlayerQuestProgress progress = getCurrentProgress(data, quest.get(), true);
        if (!progress.isAccepted() || progress.isCompleted() || progress.isClaimed()) {
            return false;
        }

        progress.setProgress(0);
        progress.setAccepted(false);
        progress.setCompleted(false);
        progress.setClaimed(false);
        playerDataManager.savePlayerData(player.getUniqueId());

        player.sendMessage(configManager.getMessage(
                "messages.quest-abandoned",
                Map.of("quest", quest.get().displayName()),
                "<yellow>Mission verworfen: <white>%quest%</white>"
        ));
        return true;
    }

    public boolean claimQuest(final Player player, final String questId) {
        final Optional<Quest> quest = getQuest(questId);
        if (quest.isEmpty()) {
            return false;
        }

        final PlayerJobData data = playerDataManager.getOrCreateData(player.getUniqueId());
        final PlayerQuestProgress progress = getCurrentProgress(data, quest.get(), true);

        if (progress.isClaimed()) {
            player.sendMessage(configManager.getMessage(
                    "messages.quest-cycle-finished",
                    Map.of("quest", quest.get().displayName(), "period", quest.get().period().getDisplayName()),
                    "<yellow>Diese %period%-Mission <white>%quest%</white><yellow> ist in diesem Zyklus bereits erledigt.</yellow>"
            ));
            return false;
        }

        if (!progress.isCompleted()) {
            player.sendMessage(configManager.getMessage(
                    "messages.quest-not-ready",
                    Map.of("quest", quest.get().displayName()),
                    "<red>Die Mission <white>%quest%</white><red> ist noch nicht abgeschlossen.</red>"
            ));
            return false;
        }

        final int rewardXp = Math.max(0, quest.get().rewardXp());
        final int grantedXp = rewardXp > 0 ? jobManager.grantDirectExperience(player, quest.get().job(), rewardXp) : 0;

        progress.setAccepted(false);
        progress.setCompleted(true);
        progress.setClaimed(true);
        data.incrementTotalQuestClaims();
        playerDataManager.savePlayerData(player.getUniqueId());
        questFeedbackManager.playClaimAnimation(player, quest.get(), grantedXp);

        player.sendMessage(configManager.getMessage(
                "messages.quest-claimed",
                Map.of(
                        "quest", quest.get().displayName(),
                        "job", configManager.getJobDisplayName(quest.get().job()),
                        "xp", String.valueOf(grantedXp)
                ),
                "<green>Mission abgegeben: <white>%quest%</white><gray> (+%xp% %job%-XP)</gray>"
        ));

        if (!quest.get().rewardMessage().isBlank()) {
            player.sendMessage(configManager.deserialize(quest.get().rewardMessage(), Map.of(
                    "quest", quest.get().displayName(),
                    "job", configManager.getJobDisplayName(quest.get().job()),
                    "xp", String.valueOf(grantedXp),
                    "period", quest.get().period().getDisplayName()
            )));
        }
        return true;
    }

    public void recordObjective(
            final Player player,
            final Job job,
            final QuestObjectiveType objectiveType,
            final String target,
            final int amount
    ) {
        if (amount <= 0) {
            return;
        }

        final String normalizedTarget = target == null ? "" : target.toUpperCase(Locale.ROOT);
        final PlayerJobData data = playerDataManager.getOrCreateData(player.getUniqueId());
        boolean changed = false;
        boolean completedQuest = false;

        for (final Quest quest : activeQuestsById.values()) {
            if (quest.job() != job || quest.objectiveType() != objectiveType || !quest.matchesTarget(normalizedTarget)) {
                continue;
            }

            final PlayerQuestProgress progress = getCurrentProgress(data, quest, true);
            if (!progress.isAccepted() || progress.isCompleted() || progress.isClaimed()) {
                continue;
            }

            final int previous = progress.getProgress();
            if (previous >= quest.requiredAmount()) {
                continue;
            }

            final int updated = Math.min(quest.requiredAmount(), previous + amount);
            progress.setProgress(updated);
            changed = true;

            if (updated >= quest.requiredAmount()) {
                progress.setAccepted(false);
                progress.setCompleted(true);
                completedQuest = true;
                questFeedbackManager.handleQuestCompleted(player, quest);
                player.sendMessage(configManager.getMessage(
                        "messages.quest-completed",
                        Map.of("quest", quest.displayName()),
                        "<gold>Mission abgeschlossen: <white>%quest%</white><gray> Kehre zum Quest-NPC zurück.</gray>"
                ));
            }
        }

        if (changed || completedQuest) {
            playerDataManager.savePlayerData(player.getUniqueId());
        }
    }

    private PlayerQuestProgress getCurrentProgress(
            final PlayerJobData data,
            final Quest quest,
            final boolean createIfMissing
    ) {
        final PlayerQuestProgress progress = createIfMissing
                ? data.getOrCreateQuestProgress(quest.id())
                : data.getQuestProgress(quest.id());
        if (progress == null) {
            return null;
        }

        final String currentCycleKey = currentCycleKey(quest.period());
        if (!currentCycleKey.equals(progress.getCycleKey())) {
            progress.setProgress(0);
            progress.setAccepted(false);
            progress.setCompleted(false);
            progress.setClaimed(false);
            progress.setCycleKey(currentCycleKey);
        }

        return progress;
    }

    private String currentCycleKey(final QuestPeriod period) {
        return period.currentCycleKey(zoneId);
    }

    private List<Quest> parseQuestCandidates(final QuestPeriod period, final ConfigurationSection section) {
        final ConfigurationSection poolSection = section.getConfigurationSection("pool");
        if (poolSection == null) {
            return parseQuest(period, period.getId(), section)
                    .map(List::of)
                    .orElseGet(List::of);
        }

        final List<Quest> quests = new ArrayList<>();
        for (final String key : poolSection.getKeys(false)) {
            final ConfigurationSection questSection = poolSection.getConfigurationSection(key);
            if (questSection == null) {
                plugin.getLogger().warning("Mission '" + period.getId() + "." + key + "' ist kein gültiger Bereich.");
                continue;
            }

            parseQuest(period, key, questSection).ifPresent(quests::add);
        }
        return quests;
    }

    private Quest selectActiveQuest(final QuestPeriod period, final List<Quest> candidates) {
        final String cycleKey = currentCycleKey(period);
        return candidates.stream()
                .min(Comparator
                        .comparingLong((Quest quest) -> Integer.toUnsignedLong((cycleKey + "|" + quest.id()).hashCode()))
                        .thenComparing(Quest::id))
                .orElse(candidates.getFirst());
    }

    private Optional<Quest> parseQuest(
            final QuestPeriod period,
            final String rawQuestId,
            final ConfigurationSection section
    ) {
        final Optional<Job> job = Job.fromId(section.getString("job"));
        final Optional<QuestObjectiveType> objectiveType = QuestObjectiveType.fromConfig(section.getString("objective-type"));
        final Material icon = Material.matchMaterial(section.getString("icon", period.getBadgeMaterial().name()));
        final String displayName = section.getString("display-name", period.getDisplayName());
        final int requiredAmount = Math.max(1, section.getInt("required-amount", 1));
        final int rewardXp = Math.max(0, section.getInt("reward-xp", 0));

        if (job.isEmpty() || objectiveType.isEmpty() || icon == null) {
            plugin.getLogger().warning("Ungültige Missions-Konfiguration: " + period.getId() + "." + rawQuestId);
            return Optional.empty();
        }

        final List<String> description = new ArrayList<>(section.getStringList("description"));
        if (description.isEmpty()) {
            final String singleDescription = section.getString("description", "");
            if (!singleDescription.isBlank()) {
                description.add(singleDescription);
            }
        }

        final Set<String> targets = section.getStringList("targets").stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        final String questId = normalizeQuestId(period, rawQuestId);
        return Optional.of(new Quest(
                questId,
                period,
                displayName,
                icon,
                job.get(),
                objectiveType.get(),
                Set.copyOf(targets),
                requiredAmount,
                rewardXp,
                section.getString("reward-message", ""),
                List.copyOf(description)
        ));
    }

    private String normalizeQuestId(final QuestPeriod period, final String rawQuestId) {
        if (rawQuestId == null || rawQuestId.isBlank()) {
            return period.getId();
        }
        return (period.getId() + "_" + rawQuestId)
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
    }
}

