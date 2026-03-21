package de.derbenson.jobcore;

import org.bukkit.Material;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;

public enum QuestPeriod {
    DAILY("daily", "Daily", Material.CLOCK, "<aqua>"),
    WEEKLY("weekly", "Weekly", Material.COMPASS, "<gold>"),
    MONTHLY("monthly", "Monatlich", Material.AMETHYST_SHARD, "<light_purple>");

    private final String id;
    private final String displayName;
    private final Material badgeMaterial;
    private final String colorPrefix;

    QuestPeriod(final String id, final String displayName, final Material badgeMaterial, final String colorPrefix) {
        this.id = id;
        this.displayName = displayName;
        this.badgeMaterial = badgeMaterial;
        this.colorPrefix = colorPrefix;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getBadgeMaterial() {
        return badgeMaterial;
    }

    public String getColorPrefix() {
        return colorPrefix;
    }

    public String currentCycleKey(final ZoneId zoneId) {
        return switch (this) {
            case DAILY -> LocalDate.now(zoneId).toString();
            case WEEKLY -> {
                final LocalDate date = LocalDate.now(zoneId);
                final WeekFields weekFields = WeekFields.ISO;
                yield date.get(weekFields.weekBasedYear()) + "-W" + date.get(weekFields.weekOfWeekBasedYear());
            }
            case MONTHLY -> YearMonth.now(zoneId).toString();
        };
    }

    public LocalDateTime nextReset(final ZoneId zoneId) {
        return switch (this) {
            case DAILY -> LocalDate.now(zoneId).plusDays(1).atStartOfDay();
            case WEEKLY -> LocalDate.now(zoneId).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay();
            case MONTHLY -> YearMonth.now(zoneId).plusMonths(1).atDay(1).atStartOfDay();
        };
    }

    public static QuestPeriod fromId(final String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        final String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (final QuestPeriod period : values()) {
            if (period.id.equals(normalized)) {
                return period;
            }
        }
        return null;
    }
}
