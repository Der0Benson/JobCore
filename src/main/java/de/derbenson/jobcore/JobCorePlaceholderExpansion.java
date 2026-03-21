package de.derbenson.jobcore;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;
import java.util.Map;

public final class JobCorePlaceholderExpansion extends PlaceholderExpansion {

    private final JobCore plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final JobManager jobManager;
    private final QuestManager questManager;

    public JobCorePlaceholderExpansion(
            final JobCore plugin,
            final ConfigManager configManager,
            final PlayerDataManager playerDataManager,
            final JobManager jobManager,
            final QuestManager questManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.jobManager = jobManager;
        this.questManager = questManager;
    }

    @Override
    public String getIdentifier() {
        return "jobcore";
    }

    @Override
    public String getAuthor() {
        return "derbenson";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(final OfflinePlayer player, final String params) {
        if (params == null || params.isBlank()) {
            return null;
        }

        final String[] parts = params.toLowerCase(Locale.ROOT).split("_");
        if (parts.length == 0) {
            return null;
        }

        return switch (parts[0]) {
            case "total" -> resolveTotalPlaceholder(player, parts);
            case "highest" -> resolveHighestPlaceholder(player, parts);
            case "bossbar" -> resolveBossBarPlaceholder(player, parts);
            case "quests" -> resolveQuestCountPlaceholder(player, parts);
            case "job" -> resolveJobPlaceholder(player, parts);
            case "quest" -> resolveQuestPlaceholder(player, parts);
            default -> null;
        };
    }

    private String resolveTotalPlaceholder(final OfflinePlayer player, final String[] parts) {
        if (!suffix(parts, 1).equals("level")) {
            return null;
        }

        final PlayerJobData data = requirePlayerData(player);
        if (data == null) {
            return null;
        }

        int totalLevel = 0;
        for (final Job job : jobManager.getJobs()) {
            totalLevel += jobManager.getProgress(data, job).getLevel();
        }
        return String.valueOf(totalLevel);
    }

    private String resolveHighestPlaceholder(final OfflinePlayer player, final String[] parts) {
        final PlayerJobData data = requirePlayerData(player);
        if (data == null || parts.length < 2 || !parts[0].equals("highest")) {
            return null;
        }

        Job highestJob = null;
        int highestLevel = -1;
        long highestXp = -1L;
        for (final Job job : jobManager.getJobs()) {
            final JobProgress progress = jobManager.getProgress(data, job);
            if (progress.getLevel() > highestLevel || (progress.getLevel() == highestLevel && progress.getXp() > highestXp)) {
                highestJob = job;
                highestLevel = progress.getLevel();
                highestXp = progress.getXp();
            }
        }

        if (highestJob == null) {
            return parts.length >= 3 && parts[1].equals("job") && parts[2].equals("level") ? "0" : "";
        }

        final String suffix = suffix(parts, 1);
        if (suffix.equals("job") || suffix.equals("job_name") || suffix.equals("job_display_name")) {
            return configManager.getJobDisplayName(highestJob);
        }
        if (suffix.equals("job_id")) {
            return highestJob.getId();
        }
        if (suffix.equals("job_level")) {
            return String.valueOf(highestLevel);
        }
        return null;
    }

    private String resolveBossBarPlaceholder(final OfflinePlayer player, final String[] parts) {
        if (!suffix(parts, 1).equals("enabled") || player == null) {
            return null;
        }
        return String.valueOf(playerDataManager.isBossBarEnabled(player.getUniqueId()));
    }

    private String resolveQuestCountPlaceholder(final OfflinePlayer player, final String[] parts) {
        if (player == null) {
            return null;
        }

        return switch (suffix(parts, 1)) {
            case "active" -> String.valueOf(questManager.getActiveQuestCount(player.getUniqueId()));
            case "claimable" -> String.valueOf(questManager.getClaimableQuestCount(player.getUniqueId()));
            case "claimed" -> String.valueOf(questManager.getClaimedQuestCount(player.getUniqueId()));
            case "total_claims", "totalclaims" -> String.valueOf(requirePlayerData(player).getTotalQuestClaims());
            default -> null;
        };
    }

    private String resolveJobPlaceholder(final OfflinePlayer player, final String[] parts) {
        if (parts.length < 3) {
            return null;
        }

        final Job job = Job.fromId(parts[1]).orElse(null);
        if (job == null) {
            return null;
        }

        final String suffix = suffix(parts, 2);
        if (suffix.equals("name") || suffix.equals("display_name")) {
            return configManager.getJobDisplayName(job);
        }
        if (suffix.equals("id")) {
            return job.getId();
        }

        final PlayerJobData data = requirePlayerData(player);
        if (data == null) {
            return null;
        }

        final JobProgress progress = jobManager.getProgress(data, job);
        final long neededXp = jobManager.getXpForNextLevel(progress.getLevel());
        return switch (suffix) {
            case "level" -> String.valueOf(progress.getLevel());
            case "xp" -> String.valueOf(progress.getXp());
            case "xp_needed", "xpneeded" -> String.valueOf(neededXp);
            case "xp_remaining", "xpremaining" -> String.valueOf(Math.max(0L, neededXp - progress.getXp()));
            case "progress_percent", "progresspercent" -> formatPercent(progress.getXp(), neededXp);
            case "is_max", "ismax" -> String.valueOf(jobManager.isMaxLevel(progress.getLevel()));
            default -> null;
        };
    }

    private String resolveQuestPlaceholder(final OfflinePlayer player, final String[] parts) {
        if (parts.length < 3) {
            return null;
        }

        final QuestPeriod period = QuestPeriod.fromId(parts[1]);
        if (period == null) {
            return null;
        }

        final Quest quest = questManager.getActiveQuest(period).orElse(null);
        if (quest == null) {
            return null;
        }

        final String suffix = suffix(parts, 2);
        return switch (suffix) {
            case "name" -> quest.displayName();
            case "id" -> quest.id();
            case "job", "job_name", "job_display_name" -> configManager.getJobDisplayName(quest.job());
            case "job_id", "jobid" -> quest.job().getId();
            case "type" -> quest.objectiveType().name();
            case "required" -> String.valueOf(quest.requiredAmount());
            case "reward_xp", "rewardxp" -> String.valueOf(quest.rewardXp());
            case "status", "progress", "remaining", "progress_percent", "progresspercent" -> resolvePlayerQuestPlaceholder(player, quest, suffix);
            default -> null;
        };
    }

    private String resolvePlayerQuestPlaceholder(
            final OfflinePlayer player,
            final Quest quest,
            final String placeholderType
    ) {
        if (player == null) {
            return null;
        }

        final PlayerQuestProgress progress = questManager.getProgress(player.getUniqueId(), quest.id());
        if (progress == null) {
            return null;
        }

        return switch (placeholderType) {
            case "status" -> questStatus(progress);
            case "progress" -> String.valueOf(progress.getProgress());
            case "remaining" -> String.valueOf(Math.max(0, quest.requiredAmount() - progress.getProgress()));
            case "progress_percent", "progresspercent" -> formatPercent(progress.getProgress(), quest.requiredAmount());
            default -> null;
        };
    }

    private String questStatus(final PlayerQuestProgress progress) {
        if (progress.isClaimed()) {
            return "claimed";
        }
        if (progress.isCompleted()) {
            return "claimable";
        }
        if (progress.isAccepted()) {
            return "active";
        }
        return "available";
    }

    private String formatPercent(final long current, final long max) {
        if (max <= 0L) {
            return "100.00";
        }

        final double percent = (current * 100.0D) / max;
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0D, Math.min(100.0D, percent)));
    }

    private PlayerJobData requirePlayerData(final OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        return playerDataManager.getOrCreateData(player.getUniqueId());
    }

    private String suffix(final String[] parts, final int fromIndex) {
        if (fromIndex >= parts.length) {
            return "";
        }
        return String.join("_", java.util.Arrays.copyOfRange(parts, fromIndex, parts.length));
    }
}
