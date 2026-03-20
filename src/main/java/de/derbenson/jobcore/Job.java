package de.derbenson.jobcore;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum Job {
    WOODCUTTER(
            "woodcutter",
            "HolzfÃ¤ller",
            Material.IRON_AXE,
            BossBar.Color.GREEN,
            createWoodcutterDefaults(),
            createMaterialSet(
                    Material.OAK_LOG,
                    Material.SPRUCE_LOG,
                    Material.BIRCH_LOG,
                    Material.JUNGLE_LOG,
                    Material.ACACIA_LOG,
                    Material.DARK_OAK_LOG,
                    Material.MANGROVE_LOG,
                    Material.CHERRY_LOG,
                    Material.PALE_OAK_LOG,
                    Material.CRIMSON_STEM,
                    Material.WARPED_STEM,
                    Material.BAMBOO_BLOCK
            ),
            Set.of(),
            List.of(
                    new JobPerk(10, PerkType.XP_BOOST, 0.02D, "<green>+2% Job-XP"),
                    new JobPerk(25, PerkType.DOUBLE_DROP_CHANCE, 0.02D, "<green>2% Chance auf doppelte Drops"),
                    new JobPerk(50, PerkType.BONUS_DROP_CHANCE, 0.05D, "<green>5% Chance auf Bonus-Drops")
            ),
            createWoodcutterPathRewardDefaults(),
            List.of(
                    new BonusDropEntry(Material.STICK, 2, 50),
                    new BonusDropEntry(Material.APPLE, 1, 35),
                    new BonusDropEntry(Material.MOSS_BLOCK, 1, 15)
            )
    ),
    MINER(
            "miner",
            "Miner",
            Material.DIAMOND_PICKAXE,
            BossBar.Color.BLUE,
            createMinerDefaults(),
            createMaterialSet(
                    Material.STONE,
                    Material.DEEPSLATE,
                    Material.TUFF,
                    Material.COAL_ORE,
                    Material.DEEPSLATE_COAL_ORE,
                    Material.COPPER_ORE,
                    Material.DEEPSLATE_COPPER_ORE,
                    Material.IRON_ORE,
                    Material.DEEPSLATE_IRON_ORE,
                    Material.GOLD_ORE,
                    Material.DEEPSLATE_GOLD_ORE,
                    Material.REDSTONE_ORE,
                    Material.DEEPSLATE_REDSTONE_ORE,
                    Material.LAPIS_ORE,
                    Material.DEEPSLATE_LAPIS_ORE,
                    Material.DIAMOND_ORE,
                    Material.DEEPSLATE_DIAMOND_ORE,
                    Material.EMERALD_ORE,
                    Material.DEEPSLATE_EMERALD_ORE,
                    Material.NETHER_GOLD_ORE,
                    Material.NETHER_QUARTZ_ORE,
                    Material.ANCIENT_DEBRIS
            ),
            Set.of(),
            List.of(
                    new JobPerk(10, PerkType.XP_BOOST, 0.02D, "<blue>+2% Job-XP"),
                    new JobPerk(25, PerkType.DOUBLE_DROP_CHANCE, 0.02D, "<blue>2% Chance auf doppelte Erze"),
                    new JobPerk(50, PerkType.BONUS_DROP_CHANCE, 0.05D, "<blue>5% Chance auf Bonus-Drops")
            ),
            createMinerPathRewardDefaults(),
            List.of(
                    new BonusDropEntry(Material.FLINT, 2, 40),
                    new BonusDropEntry(Material.RAW_COPPER, 2, 35),
                    new BonusDropEntry(Material.RAW_IRON, 1, 25)
            )
    ),
    FARMER(
            "farmer",
            "Farmer",
            Material.GOLDEN_HOE,
            BossBar.Color.YELLOW,
            createFarmerDefaults(),
            createMaterialSet(
                    Material.MELON,
                    Material.PUMPKIN,
                    Material.SUGAR_CANE,
                    Material.CACTUS
            ),
            createMaterialSet(
                    Material.WHEAT,
                    Material.CARROTS,
                    Material.POTATOES,
                    Material.BEETROOTS,
                    Material.NETHER_WART,
                    Material.COCOA,
                    Material.SWEET_BERRY_BUSH
            ),
            List.of(
                    new JobPerk(10, PerkType.XP_BOOST, 0.02D, "<yellow>+2% Job-XP"),
                    new JobPerk(25, PerkType.DOUBLE_DROP_CHANCE, 0.02D, "<yellow>2% Chance auf doppelte Ernte"),
                    new JobPerk(50, PerkType.BONUS_DROP_CHANCE, 0.05D, "<yellow>5% Chance auf Bonus-Ernte")
            ),
            createFarmerPathRewardDefaults(),
            List.of(
                    new BonusDropEntry(Material.BONE_MEAL, 4, 45),
                    new BonusDropEntry(Material.HAY_BLOCK, 1, 30),
                    new BonusDropEntry(Material.GOLDEN_CARROT, 2, 25)
            )
    );

    private final String id;
    private final String displayName;
    private final Material icon;
    private final BossBar.Color barColor;
    private final Map<Material, Integer> defaultXpValues;
    private final Set<Material> defaultPlacedProtectionMaterials;
    private final Set<Material> defaultMatureHarvestMaterials;
    private final List<JobPerk> defaultPerks;
    private final Map<Integer, List<PathReward>> defaultPathRewards;
    private final List<BonusDropEntry> defaultBonusDrops;

    Job(
            final String id,
            final String displayName,
            final Material icon,
            final BossBar.Color barColor,
            final Map<Material, Integer> defaultXpValues,
            final Set<Material> defaultPlacedProtectionMaterials,
            final Set<Material> defaultMatureHarvestMaterials,
            final List<JobPerk> defaultPerks,
            final Map<Integer, List<PathReward>> defaultPathRewards,
            final List<BonusDropEntry> defaultBonusDrops
    ) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.barColor = barColor;
        this.defaultXpValues = Collections.unmodifiableMap(defaultXpValues);
        this.defaultPlacedProtectionMaterials = Collections.unmodifiableSet(defaultPlacedProtectionMaterials);
        this.defaultMatureHarvestMaterials = Collections.unmodifiableSet(defaultMatureHarvestMaterials);
        this.defaultPerks = List.copyOf(defaultPerks);
        this.defaultPathRewards = copyRewardMap(defaultPathRewards);
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

    public Set<Material> getDefaultPlacedProtectionMaterials() {
        return defaultPlacedProtectionMaterials;
    }

    public Set<Material> getDefaultMatureHarvestMaterials() {
        return defaultMatureHarvestMaterials;
    }

    public List<JobPerk> getDefaultPerks() {
        return defaultPerks;
    }

    public Map<Integer, List<PathReward>> getDefaultPathRewards() {
        return defaultPathRewards;
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

    private static Map<Material, Integer> createMinerDefaults() {
        final Map<Material, Integer> values = new EnumMap<>(Material.class);
        values.put(Material.STONE, 1);
        values.put(Material.DEEPSLATE, 2);
        values.put(Material.TUFF, 2);
        values.put(Material.COAL_ORE, 8);
        values.put(Material.DEEPSLATE_COAL_ORE, 9);
        values.put(Material.COPPER_ORE, 9);
        values.put(Material.DEEPSLATE_COPPER_ORE, 10);
        values.put(Material.IRON_ORE, 10);
        values.put(Material.DEEPSLATE_IRON_ORE, 11);
        values.put(Material.GOLD_ORE, 12);
        values.put(Material.DEEPSLATE_GOLD_ORE, 13);
        values.put(Material.REDSTONE_ORE, 10);
        values.put(Material.DEEPSLATE_REDSTONE_ORE, 11);
        values.put(Material.LAPIS_ORE, 12);
        values.put(Material.DEEPSLATE_LAPIS_ORE, 13);
        values.put(Material.DIAMOND_ORE, 16);
        values.put(Material.DEEPSLATE_DIAMOND_ORE, 18);
        values.put(Material.EMERALD_ORE, 18);
        values.put(Material.DEEPSLATE_EMERALD_ORE, 20);
        values.put(Material.NETHER_GOLD_ORE, 10);
        values.put(Material.NETHER_QUARTZ_ORE, 8);
        values.put(Material.ANCIENT_DEBRIS, 30);
        return values;
    }

    private static Map<Material, Integer> createFarmerDefaults() {
        final Map<Material, Integer> values = new EnumMap<>(Material.class);
        values.put(Material.WHEAT, 8);
        values.put(Material.CARROTS, 7);
        values.put(Material.POTATOES, 7);
        values.put(Material.BEETROOTS, 9);
        values.put(Material.NETHER_WART, 8);
        values.put(Material.COCOA, 9);
        values.put(Material.SWEET_BERRY_BUSH, 7);
        values.put(Material.MELON, 10);
        values.put(Material.PUMPKIN, 11);
        values.put(Material.SUGAR_CANE, 5);
        values.put(Material.CACTUS, 5);
        return values;
    }

    private static Map<Integer, List<PathReward>> createWoodcutterPathRewardDefaults() {
        return Map.of(
                5, List.of(new PathReward(PathRewardType.MESSAGE, "<aqua>Willkommens-Belohnung", "<green>Du hast die ersten Stufen des HolzfÃ¤ller-Pfads erreicht.", null, 0)),
                15, List.of(new PathReward(PathRewardType.ITEM, "<green>8 Eichensetzlinge", "", Material.OAK_SAPLING, 8)),
                30, List.of(new PathReward(PathRewardType.ITEM, "<green>16 StÃ¶cke", "", Material.STICK, 16)),
                50, List.of(new PathReward(PathRewardType.MESSAGE, "<gold>Meilenstein Level 50", "<gold>Du hast Level 50 als HolzfÃ¤ller erreicht.", null, 0)),
                75, List.of(new PathReward(PathRewardType.ITEM, "<green>4 MoosblÃ¶cke", "", Material.MOSS_BLOCK, 4)),
                100, List.of(new PathReward(PathRewardType.MESSAGE, "<yellow>HolzfÃ¤ller-Meister", "<yellow>Du hast den HolzfÃ¤ller-Pfad auf Level 100 abgeschlossen.", null, 0))
        );
    }

    private static Map<Integer, List<PathReward>> createMinerPathRewardDefaults() {
        return Map.of(
                5, List.of(new PathReward(PathRewardType.MESSAGE, "<aqua>Unter Tage angekommen", "<blue>Du hast die ersten Tiefen des Miner-Pfads erreicht.", null, 0)),
                15, List.of(new PathReward(PathRewardType.ITEM, "<blue>16 Fackeln", "", Material.TORCH, 16)),
                30, List.of(new PathReward(PathRewardType.ITEM, "<blue>8 rohe Eisen", "", Material.RAW_IRON, 8)),
                50, List.of(new PathReward(PathRewardType.MESSAGE, "<gold>Meilenstein Level 50", "<gold>Du hast Level 50 als Miner erreicht.", null, 0)),
                75, List.of(new PathReward(PathRewardType.ITEM, "<blue>4 Diamanten", "", Material.DIAMOND, 4)),
                100, List.of(new PathReward(PathRewardType.MESSAGE, "<yellow>Meister der Tiefe", "<yellow>Du hast den Miner-Pfad auf Level 100 abgeschlossen.", null, 0))
        );
    }

    private static Map<Integer, List<PathReward>> createFarmerPathRewardDefaults() {
        return Map.of(
                5, List.of(new PathReward(PathRewardType.MESSAGE, "<aqua>Erste Ernte", "<yellow>Du hast die ersten Stufen des Farmer-Pfads erreicht.", null, 0)),
                15, List.of(new PathReward(PathRewardType.ITEM, "<yellow>16 Knochenmehl", "", Material.BONE_MEAL, 16)),
                30, List.of(new PathReward(PathRewardType.ITEM, "<yellow>8 Brote", "", Material.BREAD, 8)),
                50, List.of(new PathReward(PathRewardType.MESSAGE, "<gold>Meilenstein Level 50", "<gold>Du hast Level 50 als Farmer erreicht.", null, 0)),
                75, List.of(new PathReward(PathRewardType.ITEM, "<yellow>4 goldene Karotten", "", Material.GOLDEN_CARROT, 4)),
                100, List.of(new PathReward(PathRewardType.MESSAGE, "<yellow>Meister der Felder", "<yellow>Du hast den Farmer-Pfad auf Level 100 abgeschlossen.", null, 0))
        );
    }

    private static Set<Material> createMaterialSet(final Material... materials) {
        final Set<Material> values = new HashSet<>();
        Collections.addAll(values, materials);
        return values;
    }

    private static Map<Integer, List<PathReward>> copyRewardMap(final Map<Integer, List<PathReward>> input) {
        return input.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }
}

