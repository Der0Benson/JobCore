package de.derbenson.jobcore;

import org.bukkit.Material;

public record PathReward(
        PathRewardType type,
        String display,
        String value,
        Material material,
        int amount
) {
    public PathReward {
        display = display == null ? "" : display;
        value = value == null ? "" : value;
        amount = Math.max(0, amount);
    }
}

