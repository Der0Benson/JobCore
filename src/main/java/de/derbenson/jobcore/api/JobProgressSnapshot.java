package de.derbenson.jobcore.api;

public record JobProgressSnapshot(
        int level,
        long xp,
        long neededXp,
        double fractionalXp,
        boolean maxLevel
) {
}
