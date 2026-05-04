package de.derbenson.jobcore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PlayerJobData {

    private final Map<String, JobProgress> progressByJob = new HashMap<>();
    private final Map<String, PlayerQuestProgress> questProgressById = new HashMap<>();
    private final Map<String, XpBooster> xpBoostersById = new HashMap<>();
    private String lastKnownName = "";
    private boolean bossBarEnabled = true;
    private int totalQuestClaims;

    public JobProgress getOrCreateProgress(final String jobId) {
        return progressByJob.computeIfAbsent(jobId, ignored -> new JobProgress());
    }

    public void setProgress(final String jobId, final JobProgress progress) {
        progressByJob.put(jobId, progress);
    }

    public PlayerQuestProgress getOrCreateQuestProgress(final String questId) {
        return questProgressById.computeIfAbsent(questId, ignored -> new PlayerQuestProgress());
    }

    public PlayerQuestProgress getQuestProgress(final String questId) {
        return questProgressById.get(questId);
    }

    public void setQuestProgress(final String questId, final PlayerQuestProgress progress) {
        questProgressById.put(questId, progress);
    }

    public void removeQuestProgress(final String questId) {
        questProgressById.remove(questId);
    }

    public XpBooster getXpBooster(final String boosterId) {
        pruneExpiredXpBoosters();
        return xpBoostersById.get(normalizeBoosterId(boosterId));
    }

    public void setXpBooster(final String boosterId, final XpBooster booster) {
        final String normalizedBoosterId = normalizeBoosterId(boosterId);
        if (booster == null || booster.isExpired(System.currentTimeMillis())) {
            xpBoostersById.remove(normalizedBoosterId);
            return;
        }

        xpBoostersById.put(normalizedBoosterId, booster);
    }

    public void pruneExpiredXpBoosters() {
        final long nowMillis = System.currentTimeMillis();
        xpBoostersById.entrySet().removeIf(entry -> entry.getValue().isExpired(nowMillis));
    }

    public boolean isBossBarEnabled() {
        return bossBarEnabled;
    }

    public void setBossBarEnabled(final boolean bossBarEnabled) {
        this.bossBarEnabled = bossBarEnabled;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(final String lastKnownName) {
        this.lastKnownName = lastKnownName == null ? "" : lastKnownName;
    }

    public int getTotalQuestClaims() {
        return totalQuestClaims;
    }

    public void setTotalQuestClaims(final int totalQuestClaims) {
        this.totalQuestClaims = Math.max(0, totalQuestClaims);
    }

    public void incrementTotalQuestClaims() {
        totalQuestClaims++;
    }

    public Map<String, JobProgress> getProgressByJob() {
        return Collections.unmodifiableMap(progressByJob);
    }

    public Map<String, PlayerQuestProgress> getQuestProgressById() {
        return Collections.unmodifiableMap(questProgressById);
    }

    public Map<String, XpBooster> getXpBoostersById() {
        pruneExpiredXpBoosters();
        return Collections.unmodifiableMap(xpBoostersById);
    }

    public PlayerJobData copy() {
        final PlayerJobData copy = new PlayerJobData();
        copy.setLastKnownName(lastKnownName);
        copy.setBossBarEnabled(bossBarEnabled);
        copy.setTotalQuestClaims(totalQuestClaims);
        for (final Map.Entry<String, JobProgress> entry : progressByJob.entrySet()) {
            copy.setProgress(entry.getKey(), entry.getValue().copy());
        }
        for (final Map.Entry<String, PlayerQuestProgress> entry : questProgressById.entrySet()) {
            copy.setQuestProgress(entry.getKey(), entry.getValue().copy());
        }
        for (final Map.Entry<String, XpBooster> entry : getXpBoostersById().entrySet()) {
            copy.setXpBooster(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private String normalizeBoosterId(final String boosterId) {
        if (boosterId == null || boosterId.isBlank()) {
            return "global";
        }
        return boosterId.toLowerCase(java.util.Locale.ROOT);
    }
}
