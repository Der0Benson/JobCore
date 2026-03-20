package de.deinname.customjobs;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class JobManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final BossBarManager bossBarManager;
    private final Map<Job, Map<Material, Integer>> configuredXpValues = new HashMap<>();
    private final Map<Job, List<JobPerk>> configuredPerks = new HashMap<>();
    private final Map<Job, List<BonusDropEntry>> configuredBonusDrops = new HashMap<>();

    
    public JobManager(
            final JavaPlugin plugin,
            final ConfigManager configManager,
            final PlayerDataManager playerDataManager,
            final BossBarManager bossBarManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.bossBarManager = bossBarManager;
        reload();
    }

    
    public void reload() {
        configuredXpValues.clear();
        configuredPerks.clear();
        configuredBonusDrops.clear();

        for (final Job job : Job.values()) {
            configuredXpValues.put(job, loadXpValues(job));
            configuredPerks.put(job, loadPerks(job));
            configuredBonusDrops.put(job, loadBonusDrops(job));
        }
    }

    
    public List<Job> getJobs() {
        return List.of(Job.values());
    }

    
    public boolean isTrackedMaterial(final Job job, final Material material) {
        return getConfiguredXpValue(job, material) > 0;
    }

    
    public int getConfiguredXpValue(final Job job, final Material material) {
        return configuredXpValues.getOrDefault(job, job.getDefaultXpValues()).getOrDefault(material, 0);
    }

    
    public JobProgress getProgress(final UUID playerUuid, final Job job) {
        return playerDataManager.getOrCreateData(playerUuid).getOrCreateProgress(job.getId());
    }

    
    public long getXpForNextLevel(final int level) {
        return configManager.getXpFormulaBase() + ((long) Math.max(0, level) * configManager.getXpFormulaPerLevel());
    }

    
    public List<JobPerk> getPerks(final Job job) {
        return configuredPerks.getOrDefault(job, job.getDefaultPerks());
    }

    
    public double getUnlockedPerkValue(final Job job, final int level, final PerkType perkType) {
        return getPerks(job).stream()
                .filter(perk -> perk.type() == perkType)
                .filter(perk -> level >= perk.level())
                .mapToDouble(JobPerk::value)
                .sum();
    }

    
    public int grantExperience(final Player player, final Job job, final int baseAmount) {
        if (baseAmount <= 0) {
            return 0;
        }

        final JobProgress progress = getProgress(player.getUniqueId(), job);
        final double xpBoost = getUnlockedPerkValue(job, progress.getLevel(), PerkType.XP_BOOST);
        final double adjusted = (baseAmount * (1.0D + xpBoost)) + progress.getFractionalXp();
        final int granted = Math.max(0, (int) Math.floor(adjusted));
        progress.setFractionalXp(Math.max(0.0D, adjusted - granted));

        if (granted <= 0) {
            return 0;
        }

        progress.setXp(progress.getXp() + granted);

        boolean leveledUp = false;
        long neededXp = getXpForNextLevel(progress.getLevel());
        while (progress.getXp() >= neededXp) {
            progress.setXp(progress.getXp() - neededXp);
            progress.setLevel(progress.getLevel() + 1);
            leveledUp = true;
            neededXp = getXpForNextLevel(progress.getLevel());
        }

        bossBarManager.showOrUpdate(
                player,
                job,
                progress.getLevel(),
                progress.getXp(),
                neededXp,
                granted
        );

        if (leveledUp) {
            bossBarManager.handleLevelUp(player, job);
        }

        return granted;
    }

    
    public boolean shouldDoubleDrops(final UUID playerUuid, final Job job) {
        final int level = getProgress(playerUuid, job).getLevel();
        final double chance = getUnlockedPerkValue(job, level, PerkType.DOUBLE_DROP_CHANCE);
        return chance > 0.0D && ThreadLocalRandom.current().nextDouble() < chance;
    }

    
    public Optional<BonusDropEntry> rollBonusDrop(final UUID playerUuid, final Job job) {
        final int level = getProgress(playerUuid, job).getLevel();
        final double chance = getUnlockedPerkValue(job, level, PerkType.BONUS_DROP_CHANCE);
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) {
            return Optional.empty();
        }

        final List<BonusDropEntry> entries = configuredBonusDrops.getOrDefault(job, job.getDefaultBonusDrops());
        if (entries.isEmpty()) {
            return Optional.empty();
        }

        final int totalWeight = entries.stream()
                .mapToInt(BonusDropEntry::weight)
                .filter(weight -> weight > 0)
                .sum();

        if (totalWeight <= 0) {
            return Optional.empty();
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (final BonusDropEntry entry : entries) {
            if (entry.weight() <= 0) {
                continue;
            }

            roll -= entry.weight();
            if (roll < 0) {
                return Optional.of(entry);
            }
        }

        return Optional.empty();
    }

    private Map<Material, Integer> loadXpValues(final Job job) {
        final Map<Material, Integer> xpValues = new EnumMap<>(Material.class);
        xpValues.putAll(job.getDefaultXpValues());

        final ConfigurationSection section = configManager.getConfiguration()
                .getConfigurationSection("jobs." + job.getId() + ".xp-values");

        if (section == null) {
            return Map.copyOf(xpValues);
        }

        for (final String key : section.getKeys(false)) {
            final Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Unbekanntes Material in config.yml fuer Job "
                        + job.getId() + ": " + key);
                continue;
            }

            xpValues.put(material, Math.max(0, section.getInt(key, 0)));
        }

        return Map.copyOf(xpValues);
    }

    private List<JobPerk> loadPerks(final Job job) {
        final List<Map<?, ?>> rawEntries = configManager.getConfiguration().getMapList("jobs." + job.getId() + ".perks");
        if (rawEntries.isEmpty()) {
            return job.getDefaultPerks();
        }

        final List<JobPerk> perks = new ArrayList<>();
        for (final Map<?, ?> entry : rawEntries) {
            final int level = parseInt(entry.get("level"), -1);
            final double value = parseDouble(entry.get("value"), -1.0D);
            final Object displayValue = entry.containsKey("display") ? entry.get("display") : "";
            final String display = String.valueOf(displayValue);
            final String rawType = String.valueOf(entry.get("type"));
            final Optional<PerkType> perkType = PerkType.fromConfig(rawType);

            if (level < 0 || value < 0.0D || display.isBlank() || perkType.isEmpty()) {
                plugin.getLogger().warning("Ungueltiger Perk-Eintrag fuer Job " + job.getId() + ": " + entry);
                continue;
            }

            perks.add(new JobPerk(level, perkType.get(), value, display));
        }

        if (perks.isEmpty()) {
            return job.getDefaultPerks();
        }

        perks.sort(Comparator.comparingInt(JobPerk::level));
        return List.copyOf(perks);
    }

    private List<BonusDropEntry> loadBonusDrops(final Job job) {
        final List<Map<?, ?>> rawEntries = configManager.getConfiguration()
                .getMapList("jobs." + job.getId() + ".bonus-drops");

        if (rawEntries.isEmpty()) {
            return job.getDefaultBonusDrops();
        }

        final List<BonusDropEntry> bonusDrops = new ArrayList<>();
        for (final Map<?, ?> entry : rawEntries) {
            final String rawMaterial = String.valueOf(entry.get("material"));
            final Material material = Material.matchMaterial(rawMaterial);
            final int amount = parseInt(entry.get("amount"), 1);
            final int weight = parseInt(entry.get("weight"), 0);

            if (material == null || amount <= 0 || weight <= 0) {
                plugin.getLogger().warning("Ungueltiger Bonus-Drop fuer Job " + job.getId() + ": " + entry);
                continue;
            }

            bonusDrops.add(new BonusDropEntry(material, amount, weight));
        }

        if (bonusDrops.isEmpty()) {
            return job.getDefaultBonusDrops();
        }

        return List.copyOf(bonusDrops);
    }

    private int parseInt(final Object value, final int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (final NumberFormatException ignored) {
                return fallback;
            }
        }

        return fallback;
    }

    private double parseDouble(final Object value, final double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (final NumberFormatException ignored) {
                return fallback;
            }
        }

        return fallback;
    }
}
