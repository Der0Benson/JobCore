package de.derbenson.jobcore.api;

public record QuestProgressSnapshot(
        String questId,
        int progress,
        int requiredAmount,
        boolean accepted,
        boolean completed,
        boolean claimed,
        String cycleKey
) {
}
