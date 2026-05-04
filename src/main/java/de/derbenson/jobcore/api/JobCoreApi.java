package de.derbenson.jobcore.api;

import de.derbenson.jobcore.Job;
import de.derbenson.jobcore.Quest;
import de.derbenson.jobcore.QuestObjectiveType;
import de.derbenson.jobcore.QuestPeriod;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobCoreApi {

    List<Job> getJobs();

    JobProgressSnapshot getJobProgress(UUID playerUuid, Job job);

    Optional<QuestProgressSnapshot> getQuestProgress(UUID playerUuid, String questId);

    int grantDirectExperience(Player player, Job job, int amount);

    void setLevel(Player player, Job job, int level);

    void giveXpBooster(Player player, Job job, double bonusMultiplier, Duration duration);

    void giveGlobalXpBooster(Player player, double bonusMultiplier, Duration duration);

    List<Quest> getActiveQuests();

    List<Quest> getConfiguredQuests();

    Optional<Quest> getActiveQuest(QuestPeriod period);

    boolean acceptQuest(Player player, String questId);

    boolean abandonQuest(Player player, String questId);

    boolean claimQuest(Player player, String questId);

    void recordObjective(Player player, QuestObjectiveType objectiveType, String target, int amount);
}
