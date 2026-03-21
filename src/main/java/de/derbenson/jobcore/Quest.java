package de.derbenson.jobcore;

import org.bukkit.Material;

import java.util.List;
import java.util.Set;

public record Quest(
        String id,
        QuestPeriod period,
        String displayName,
        Material icon,
        Job job,
        QuestObjectiveType objectiveType,
        Set<String> targets,
        int requiredAmount,
        int rewardXp,
        String rewardMessage,
        List<String> description
) {

    public boolean matchesTarget(final String target) {
        return targets.isEmpty() || targets.contains(target);
    }
}
