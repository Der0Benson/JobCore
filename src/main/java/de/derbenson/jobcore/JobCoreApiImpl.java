package de.derbenson.jobcore;

import de.derbenson.jobcore.api.JobCoreApi;
import de.derbenson.jobcore.api.JobProgressSnapshot;
import de.derbenson.jobcore.api.QuestProgressSnapshot;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class JobCoreApiImpl implements JobCoreApi {

    private final PlayerDataManager playerDataManager;
    private final JobManager jobManager;
    private final QuestManager questManager;

    JobCoreApiImpl(
            final PlayerDataManager playerDataManager,
            final JobManager jobManager,
            final QuestManager questManager
    ) {
        this.playerDataManager = playerDataManager;
        this.jobManager = jobManager;
        this.questManager = questManager;
    }

    @Override
    public List<Job> getJobs() {
        return jobManager.getJobs();
    }

    @Override
    public JobProgressSnapshot getJobProgress(final UUID playerUuid, final Job job) {
        final JobProgress progress = jobManager.getProgress(playerUuid, job);
        final long neededXp = jobManager.getXpForNextLevel(progress.getLevel());
        return new JobProgressSnapshot(
                progress.getLevel(),
                progress.getXp(),
                neededXp,
                progress.getFractionalXp(),
                jobManager.isMaxLevel(progress.getLevel())
        );
    }

    @Override
    public Optional<QuestProgressSnapshot> getQuestProgress(final UUID playerUuid, final String questId) {
        return questManager.getQuest(questId)
                .map(quest -> toQuestProgressSnapshot(quest, questManager.getProgress(playerUuid, quest.id())));
    }

    @Override
    public int grantDirectExperience(final Player player, final Job job, final int amount) {
        final int granted = jobManager.grantDirectExperience(player, job, amount);
        playerDataManager.savePlayerData(player.getUniqueId());
        return granted;
    }

    @Override
    public void setLevel(final Player player, final Job job, final int level) {
        jobManager.setLevel(player, job, level);
        playerDataManager.savePlayerData(player.getUniqueId());
    }

    @Override
    public List<Quest> getActiveQuests() {
        return questManager.getQuests();
    }

    @Override
    public List<Quest> getConfiguredQuests() {
        return questManager.getConfiguredQuests();
    }

    @Override
    public Optional<Quest> getActiveQuest(final QuestPeriod period) {
        return questManager.getActiveQuest(period);
    }

    @Override
    public boolean acceptQuest(final Player player, final String questId) {
        return questManager.acceptQuest(player, questId);
    }

    @Override
    public boolean abandonQuest(final Player player, final String questId) {
        return questManager.abandonQuest(player, questId);
    }

    @Override
    public boolean claimQuest(final Player player, final String questId) {
        return questManager.claimQuest(player, questId);
    }

    @Override
    public void recordObjective(
            final Player player,
            final QuestObjectiveType objectiveType,
            final String target,
            final int amount
    ) {
        questManager.recordObjective(player, objectiveType, target, amount);
    }

    private QuestProgressSnapshot toQuestProgressSnapshot(
            final Quest quest,
            final PlayerQuestProgress progress
    ) {
        if (progress == null) {
            return new QuestProgressSnapshot(quest.id(), 0, quest.requiredAmount(), false, false, false, "");
        }

        return new QuestProgressSnapshot(
                quest.id(),
                progress.getProgress(),
                quest.requiredAmount(),
                progress.isAccepted(),
                progress.isCompleted(),
                progress.isClaimed(),
                progress.getCycleKey()
        );
    }
}
