package de.deinname.customjobs;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum Job {
    WOODCUTTER(
            "woodcutter",
            "Holzf\u00e4ller",
            Material.IRON_AXE,
            BossBar.Color.GREEN,
            createWoodcutterDefaults(),
            List.of(
                    new JobPerk(10, PerkType.XP_BOOST, 0.02D, "<green>+2% Job-XP"),
                    new JobPerk(25, PerkType.DOUBLE_DROP_CHANCE, 0.02D, "<green>2% Chance auf doppelte Drops"),
                    new JobPerk(50, PerkType.BONUS_DROP_CHANCE, 0.05D, "<green>5% Chance auf Bonus-Drops")
            ),
            List.of(
                    new BonusDropEntry(Material.STICK, 2, 50),
                    new BonusDropEntry(Material.APPLE, 1, 35),
                    new BonusDropEntry(Material.MOSS_BLOCK, 1, 15)
            )
    );

    private final String id;
    private final String displayName;
    private final Material icon;
    private final BossBar.Color barColor;
    private final Map<Material, Integer> defaultXpValues;
    private final List<JobPerk> defaultPerks;
    private final List<BonusDropEntry> defaultBonusDrops;

    Job(
            final String id,
            final String displayName,
            final Material icon,
            final BossBar.Color barColor,
            final Map<Material, Integer> defaultXpValues,
            final List<JobPerk> defaultPerks,
            final List<BonusDropEntry> defaultBonusDrops
    ) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.barColor = barColor;
        this.defaultXpValues = Collections.unmodifiableMap(defaultXpValues);
        this.defaultPerks = List.copyOf(defaultPerks);
        this.defaultBonusDrops = List.copyOf(defaultBonusDrops);
    }

    
    public String getId() {
        return id;
    }

    
    public String getDisplayName() {
        return displayName;
    }

    
    public Material getIcon() {
        return icon;
    }

    
    public BossBar.Color getBarColor() {
        return barColor;
    }

    
    public Map<Material, Integer> getDefaultXpValues() {
        return defaultXpValues;
    }

    
    public List<JobPerk> getDefaultPerks() {
        return defaultPerks;
    }

    
    public List<BonusDropEntry> getDefaultBonusDrops() {
        return defaultBonusDrops;
    }

    
    public static Optional<Job> fromId(final String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        final String normalized = id.toLowerCase(Locale.ROOT);
        for (final Job job : values()) {
            if (job.id.equalsIgnoreCase(normalized)) {
                return Optional.of(job);
            }
        }
        return Optional.empty();
    }

    private static Map<Material, Integer> createWoodcutterDefaults() {
        final Map<Material, Integer> values = new EnumMap<>(Material.class);
        values.put(Material.OAK_LOG, 10);
        values.put(Material.SPRUCE_LOG, 10);
        values.put(Material.BIRCH_LOG, 10);
        values.put(Material.JUNGLE_LOG, 11);
        values.put(Material.ACACIA_LOG, 11);
        values.put(Material.DARK_OAK_LOG, 12);
        values.put(Material.MANGROVE_LOG, 13);
        values.put(Material.CHERRY_LOG, 13);
        values.put(Material.PALE_OAK_LOG, 14);
        values.put(Material.CRIMSON_STEM, 12);
        values.put(Material.WARPED_STEM, 12);
        values.put(Material.BAMBOO_BLOCK, 9);
        return values;
    }
}
