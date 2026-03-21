package de.derbenson.jobcore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PlayerJobData {

    private final Map<String, JobProgress> progressByJob = new HashMap<>();
    private final Map<String, PlayerQuestProgress> questProgressById = new HashMap<>();
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
        return copy;
    }
}

