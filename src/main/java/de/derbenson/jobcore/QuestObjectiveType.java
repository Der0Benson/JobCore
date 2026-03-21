package de.derbenson.jobcore;

import java.util.Locale;
import java.util.Optional;

public enum QuestObjectiveType {
    BREAK_BLOCK,
    KILL_ENTITY,
    FISH_ITEM,
    BREW_INGREDIENT;

    public static Optional<QuestObjectiveType> fromConfig(final String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(valueOf(input.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
