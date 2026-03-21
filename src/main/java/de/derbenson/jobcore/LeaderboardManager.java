package de.derbenson.jobcore;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LeaderboardManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean();
    private volatile LeaderboardCache cache = LeaderboardCache.empty();

    public LeaderboardManager(
            final JavaPlugin plugin,
            final ConfigManager configManager,
            final PlayerDataManager playerDataManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
    }

    public void warmUp() {
        refreshAsync(true);
    }

    public void invalidate() {
        cache = cache.invalidate();
        refreshAsync(true);
    }

    public boolean isRefreshing() {
        return refreshInProgress.get();
    }

    public List<JobLeaderboardEntry> getTopPlayersForJob(final Job job, final int limit) {
        refreshIfNeeded();
        final List<JobLeaderboardEntry> entries = cache.jobEntries().getOrDefault(job, List.of());
        return entries.stream()
                .limit(Math.max(1, limit))
                .toList();
    }

    public List<QuestLeaderboardEntry> getTopQuestPlayers(final int limit) {
        refreshIfNeeded();
        return cache.questEntries().stream()
                .limit(Math.max(1, limit))
                .toList();
    }

    private void refreshIfNeeded() {
        if (System.currentTimeMillis() > cache.validUntil()) {
            refreshAsync(false);
        }
    }

    private void refreshAsync(final boolean forceRefresh) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            return;
        }

        playerDataManager.getAllPlayerDataSnapshotAsync(forceRefresh).whenComplete((snapshot, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("Bestenlisten konnten nicht aktualisiert werden: " + throwable.getMessage());
                refreshInProgress.set(false);
                return;
            }

            cache = buildCache(snapshot);
            refreshInProgress.set(false);
        });
    }

    private LeaderboardCache buildCache(final Map<UUID, PlayerJobData> snapshot) {
        final Map<Job, List<JobLeaderboardEntry>> jobEntries = new EnumMap<>(Job.class);
        for (final Job job : Job.values()) {
            final List<JobLeaderboardEntry> entries = snapshot.entrySet().stream()
                    .map(entry -> mapJobEntry(entry.getKey(), entry.getValue(), job))
                    .filter(entry -> entry.level() > 0 || entry.xp() > 0L)
                    .sorted(Comparator
                            .comparingInt(JobLeaderboardEntry::level).reversed()
                            .thenComparing(Comparator.comparingLong(JobLeaderboardEntry::xp).reversed())
                            .thenComparing(JobLeaderboardEntry::playerName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            jobEntries.put(job, entries);
        }

        final List<QuestLeaderboardEntry> questEntries = snapshot.entrySet().stream()
                .map(entry -> mapQuestEntry(entry.getKey(), entry.getValue()))
                .filter(entry -> entry.totalClaims() > 0)
                .sorted(Comparator
                        .comparingInt(QuestLeaderboardEntry::totalClaims).reversed()
                        .thenComparing(QuestLeaderboardEntry::playerName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new LeaderboardCache(
                Map.copyOf(jobEntries),
                questEntries,
                System.currentTimeMillis() + configManager.getLeaderboardCacheMillis()
        );
    }

    private JobLeaderboardEntry mapJobEntry(final UUID playerUuid, final PlayerJobData data, final Job job) {
        final JobProgress progress = data.getProgressByJob().getOrDefault(job.getId(), new JobProgress());
        return new JobLeaderboardEntry(playerUuid, resolvePlayerName(playerUuid, data), progress.getLevel(), progress.getXp());
    }

    private QuestLeaderboardEntry mapQuestEntry(final UUID playerUuid, final PlayerJobData data) {
        return new QuestLeaderboardEntry(playerUuid, resolvePlayerName(playerUuid, data), data.getTotalQuestClaims());
    }

    private String resolvePlayerName(final UUID playerUuid, final PlayerJobData data) {
        if (data.getLastKnownName() != null && !data.getLastKnownName().isBlank()) {
            return data.getLastKnownName();
        }

        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
        if (offlinePlayer.getName() != null && !offlinePlayer.getName().isBlank()) {
            return offlinePlayer.getName();
        }

        return playerUuid.toString().substring(0, 8);
    }

    public record JobLeaderboardEntry(UUID playerUuid, String playerName, int level, long xp) {
    }

    public record QuestLeaderboardEntry(UUID playerUuid, String playerName, int totalClaims) {
    }

    private record LeaderboardCache(
            Map<Job, List<JobLeaderboardEntry>> jobEntries,
            List<QuestLeaderboardEntry> questEntries,
            long validUntil
    ) {

        private static LeaderboardCache empty() {
            return new LeaderboardCache(Map.of(), List.of(), 0L);
        }

        private LeaderboardCache invalidate() {
            return new LeaderboardCache(jobEntries, questEntries, 0L);
        }
    }
}
