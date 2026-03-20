package de.derbenson.jobcore;

import java.util.Locale;
import java.util.Optional;

public enum PerkType {
    XP_BOOST,
    DOUBLE_DROP_CHANCE,
    BONUS_DROP_CHANCE;

    
    public static Optional<PerkType> fromConfig(final String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(valueOf(input.toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

