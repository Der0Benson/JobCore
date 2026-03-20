package de.deinname.customjobs;

import java.util.Locale;
import java.util.Optional;

public enum PathRewardType {
    ITEM,
    COMMAND,
    MESSAGE;

    public static Optional<PathRewardType> fromConfig(final String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(PathRewardType.valueOf(input.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
