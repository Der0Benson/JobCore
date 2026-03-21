package de.derbenson.jobcore;

public final class PlayerQuestProgress {

    private int progress;
    private boolean accepted;
    private boolean completed;
    private boolean claimed;
    private String cycleKey;

    public PlayerQuestProgress() {
        this(0, false, false, false, "");
    }

    public PlayerQuestProgress(
            final int progress,
            final boolean accepted,
            final boolean completed,
            final boolean claimed,
            final String cycleKey
    ) {
        this.progress = Math.max(0, progress);
        this.accepted = accepted;
        this.completed = completed;
        this.claimed = claimed;
        this.cycleKey = cycleKey == null ? "" : cycleKey;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(final int progress) {
        this.progress = Math.max(0, progress);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(final boolean accepted) {
        this.accepted = accepted;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(final boolean completed) {
        this.completed = completed;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(final boolean claimed) {
        this.claimed = claimed;
    }

    public String getCycleKey() {
        return cycleKey;
    }

    public void setCycleKey(final String cycleKey) {
        this.cycleKey = cycleKey == null ? "" : cycleKey;
    }

    public PlayerQuestProgress copy() {
        return new PlayerQuestProgress(progress, accepted, completed, claimed, cycleKey);
    }
}
