package de.derbenson.jobcore;

import de.derbenson.jobcore.api.event.JobCoreExperienceGainEvent;
import de.derbenson.jobcore.api.event.JobCoreLevelUpEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class JobManager {

    private static final int MAX_LEVEL = 100;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final BossBarManager bossBarManager;
    private final Map<Job, Map<Material, Integer>> configuredXpValues = new HashMap<>();
    private final Map<Job, Map<EntityType, Integer>> configuredEntityXpValues = new HashMap<>();
    private final Map<Job, Set<Material>> configuredPlacedProtection = new HashMap<>();
    private final Map<Job, Set<Material>> configuredMatureHarvest = new HashMap<>();
    private final Map<Job, List<JobPerk>> configuredPerks = new HashMap<>();
    private final Map<Job, Map<Integer, List<PathReward>>> configuredPathRewards = new HashMap<>();
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
        configuredEntityXpValues.clear();
        configuredPlacedProtection.clear();
        configuredMatureHarvest.clear();
        configuredPerks.clear();
        configuredPathRewards.clear();
        configuredBonusDrops.clear();

        for (final Job job : Job.values()) {
            configuredXpValues.put(job, loadXpValues(job));
            configuredEntityXpValues.put(job, loadEntityXpValues(job));
            configuredPlacedProtection.put(job, loadMaterialSet(job, "placed-protection", job.getDefaultPlacedProtectionMaterials()));
            configuredMatureHarvest.put(job, loadMaterialSet(job, "mature-harvest", job.getDefaultMatureHarvestMaterials()));
            configuredPerks.put(job, loadPerks(job));
            configuredPathRewards.put(job, loadPathRewards(job));
            configuredBonusDrops.put(job, loadBonusDrops(job));
        }
    }

    public List<Job> getJobs() {
        return List.of(Job.values());
    }

    public JobProgress getProgress(final UUID playerUuid, final Job job) {
        final JobProgress progress = playerDataManager.getOrCreateData(playerUuid).getOrCreateProgress(job.getId());
        clampProgress(progress);
        return progress;
    }

    public JobProgress getProgress(final PlayerJobData data, final Job job) {
        final JobProgress progress = data.getOrCreateProgress(job.getId());
        clampProgress(progress);
        return progress;
    }

    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    public boolean isMaxLevel(final int level) {
        return level >= MAX_LEVEL;
    }

    public long getXpForNextLevel(final int level) {
        if (isMaxLevel(level)) {
            return 0L;
        }
        return configManager.getXpFormulaBase() + ((long) Math.max(0, level) * configManager.getXpFormulaPerLevel());
    }

    public boolean isTrackedMaterial(final Job job, final Material material) {
        return getConfiguredXpValue(job, material) > 0;
    }

    public boolean isTrackedEntity(final Job job, final EntityType entityType) {
        return getConfiguredXpValue(job, entityType) > 0;
    }

    public int getConfiguredXpValue(final Job job, final Material material) {
        return configuredXpValues.getOrDefault(job, job.getDefaultXpValues()).getOrDefault(material, 0);
    }

    public int getConfiguredXpValue(final Job job, final EntityType entityType) {
        return configuredEntityXpValues.getOrDefault(job, job.getDefaultEntityXpValues()).getOrDefault(entityType, 0);
    }

    public boolean isPlacedProtected(final Job job, final Material material) {
        return configuredPlacedProtection.getOrDefault(job, job.getDefaultPlacedProtectionMaterials()).contains(material);
    }

    public boolean requiresMatureHarvest(final Job job, final Material material) {
        return configuredMatureHarvest.getOrDefault(job, job.getDefaultMatureHarvestMaterials()).contains(material);
    }

    public boolean shouldTrackPlacedBlock(final Material material) {
        for (final Job job : Job.values()) {
            if (isPlacedProtected(job, material) || isTrackedMaterial(job, material)) {
                return true;
            }
        }
        return false;
    }

    public List<JobPerk> getPerks(final Job job) {
        return configuredPerks.getOrDefault(job, job.getDefaultPerks());
    }

    public List<PathReward> getPathRewards(final Job job, final int level) {
        return configuredPathRewards.getOrDefault(job, job.getDefaultPathRewards()).getOrDefault(level, List.of());
    }

    public double getUnlockedPerkValue(final Job job, final int level, final PerkType perkType) {
        return getPerks(job).stream()
                .filter(perk -> perk.type() == perkType)
                .filter(perk -> level >= perk.level())
                .mapToDouble(JobPerk::value)
                .sum();
    }

    public int grantExperience(final Player player, final Job job, final int baseAmount) {
        return applyExperience(playerDataManager.getOrCreateData(player.getUniqueId()), player.getUniqueId(), player, job, baseAmount, true, playerDataManager.isBossBarEnabled(player.getUniqueId()));
    }

    public int grantDirectExperience(final Player player, final Job job, final int amount) {
        return applyExperience(playerDataManager.getOrCreateData(player.getUniqueId()), player.getUniqueId(), player, job, amount, false, playerDataManager.isBossBarEnabled(player.getUniqueId()));
    }

    public int grantDirectExperience(final UUID playerUuid, final PlayerJobData data, final Job job, final int amount) {
        return applyExperience(data, playerUuid, null, job, amount, false, false);
    }

    public void giveXpBooster(
            final UUID playerUuid,
            final Job job,
            final double bonusMultiplier,
            final Duration duration
    ) {
        if (playerUuid == null || bonusMultiplier <= 0.0D || duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }

        giveXpBooster(playerDataManager.getOrCreateData(playerUuid), job, bonusMultiplier, duration);
    }

    public void giveXpBooster(
            final PlayerJobData data,
            final Job job,
            final double bonusMultiplier,
            final Duration duration
    ) {
        if (data == null || bonusMultiplier <= 0.0D || duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }

        final String boosterId = job == null ? "global" : job.getId();
        final long nowMillis = System.currentTimeMillis();
        final long expiresAtMillis = nowMillis + Math.max(1L, duration.toMillis());
        final XpBooster existing = data.getXpBooster(boosterId);
        if (existing == null || existing.isExpired(nowMillis)) {
            data.setXpBooster(boosterId, new XpBooster(bonusMultiplier, expiresAtMillis));
            return;
        }

        data.setXpBooster(boosterId, new XpBooster(
                Math.max(existing.bonusMultiplier(), bonusMultiplier),
                Math.max(existing.expiresAtMillis(), expiresAtMillis)
        ));
    }

    public double getActiveXpBoosterValue(final UUID playerUuid, final Job job) {
        return getActiveXpBoosterValue(playerDataManager.getOrCreateData(playerUuid), job);
    }

    public void setLevel(final Player player, final Job job, final int level) {
        setLevel(playerDataManager.getOrCreateData(player.getUniqueId()), job, level);
        bossBarManager.hide(player);
    }

    public void setLevel(final PlayerJobData data, final Job job, final int level) {
        final JobProgress progress = getProgress(data, job);
        progress.setLevel(Math.max(0, Math.min(MAX_LEVEL, level)));
        progress.setXp(0L);
        progress.setFractionalXp(0.0D);
        clampProgress(progress);
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

        final ConfigurationSection section = configManager.getJobConfiguration(job).getConfigurationSection("xp-values");
        if (section == null) {
            return Map.copyOf(xpValues);
        }

        for (final String key : section.getKeys(false)) {
            final Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Unbekanntes Material in " + job.getId() + ".yml: " + key);
                continue;
            }

            xpValues.put(material, Math.max(0, section.getInt(key, 0)));
        }

        return Map.copyOf(xpValues);
    }

    private Map<EntityType, Integer> loadEntityXpValues(final Job job) {
        final Map<EntityType, Integer> xpValues = new EnumMap<>(EntityType.class);
        xpValues.putAll(job.getDefaultEntityXpValues());

        final ConfigurationSection section = configManager.getJobConfiguration(job).getConfigurationSection("entity-xp-values");
        if (section == null) {
            return Map.copyOf(xpValues);
        }

        for (final String key : section.getKeys(false)) {
            final EntityType entityType = parseEntityType(key);
            if (entityType == null) {
                plugin.getLogger().warning("Unbekannter EntityType in " + job.getId() + ".yml: " + key);
                continue;
            }

            xpValues.put(entityType, Math.max(0, section.getInt(key, 0)));
        }

        return Map.copyOf(xpValues);
    }

    private Set<Material> loadMaterialSet(final Job job, final String path, final Set<Material> defaults) {
        final List<String> values = configManager.getJobConfiguration(job).getStringList(path);
        if (values.isEmpty()) {
            return Set.copyOf(defaults);
        }

        final Set<Material> materials = new HashSet<>();
        for (final String value : values) {
            final Material material = Material.matchMaterial(value);
            if (material == null) {
                plugin.getLogger().warning("Unbekanntes Material in " + job.getId() + ".yml unter " + path + ": " + value);
                continue;
            }
            materials.add(material);
        }

        if (materials.isEmpty()) {
            return Set.copyOf(defaults);
        }

        return Set.copyOf(materials);
    }

    private List<JobPerk> loadPerks(final Job job) {
        final List<Map<?, ?>> rawEntries = configManager.getJobConfiguration(job).getMapList("perks");
        if (rawEntries.isEmpty()) {
            return job.getDefaultPerks();
        }

        final List<JobPerk> perks = new ArrayList<>();
        for (final Map<?, ?> entry : rawEntries) {
            final int level = parseInt(entry.get("level"), -1);
            final double value = parseDouble(entry.get("value"), -1.0D);
            final String display = entry.containsKey("display") ? String.valueOf(entry.get("display")) : "";
            final Optional<PerkType> perkType = PerkType.fromConfig(String.valueOf(entry.get("type")));

            if (level < 0 || value < 0.0D || display.isBlank() || perkType.isEmpty()) {
                plugin.getLogger().warning("Ung\u00fcltiger Perk-Eintrag f\u00fcr Job " + job.getId() + ": " + entry);
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

    private Map<Integer, List<PathReward>> loadPathRewards(final Job job) {
        final FileConfiguration configuration = configManager.getJobConfiguration(job);
        final ConfigurationSection section = configuration.getConfigurationSection("path-rewards");
        if (section == null) {
            return job.getDefaultPathRewards();
        }

        final Map<Integer, List<PathReward>> pathRewards = new HashMap<>();
        for (final String key : section.getKeys(false)) {
            final int level = parseInt(key, -1);
            if (level <= 0) {
                plugin.getLogger().warning("Ung\u00fcltige Reward-Stufe f\u00fcr Job " + job.getId() + ": " + key);
                continue;
            }

            final List<Map<?, ?>> rawEntries = configuration.getMapList("path-rewards." + key);
            if (rawEntries.isEmpty()) {
                continue;
            }

            final List<PathReward> rewards = new ArrayList<>();
            for (final Map<?, ?> entry : rawEntries) {
                parsePathReward(job, level, entry).ifPresent(rewards::add);
            }

            if (!rewards.isEmpty()) {
                pathRewards.put(level, List.copyOf(rewards));
            }
        }

        if (pathRewards.isEmpty()) {
            return job.getDefaultPathRewards();
        }

        return Map.copyOf(pathRewards);
    }

    private List<BonusDropEntry> loadBonusDrops(final Job job) {
        final List<Map<?, ?>> rawEntries = configManager.getJobConfiguration(job).getMapList("bonus-drops");
        if (rawEntries.isEmpty()) {
            return job.getDefaultBonusDrops();
        }

        final List<BonusDropEntry> bonusDrops = new ArrayList<>();
        for (final Map<?, ?> entry : rawEntries) {
            final Material material = Material.matchMaterial(String.valueOf(entry.get("material")));
            final int amount = parseInt(entry.get("amount"), 1);
            final int weight = parseInt(entry.get("weight"), 0);

            if (material == null || amount <= 0 || weight <= 0) {
                plugin.getLogger().warning("Ung\u00fcltiger Bonus-Drop f\u00fcr Job " + job.getId() + ": " + entry);
                continue;
            }

            bonusDrops.add(new BonusDropEntry(material, amount, weight));
        }

        if (bonusDrops.isEmpty()) {
            return job.getDefaultBonusDrops();
        }

        return List.copyOf(bonusDrops);
    }

    private Optional<PathReward> parsePathReward(final Job job, final int level, final Map<?, ?> entry) {
        final Optional<PathRewardType> rewardType = PathRewardType.fromConfig(String.valueOf(entry.get("type")));
        if (rewardType.isEmpty()) {
            plugin.getLogger().warning("Ung\u00fcltiger Reward-Typ f\u00fcr Job " + job.getId() + " auf Level " + level + ": " + entry);
            return Optional.empty();
        }

        final String rawDisplay = entry.containsKey("display") ? String.valueOf(entry.get("display")) : "";
        if (rewardType.get() == PathRewardType.ITEM) {
            final Material material = Material.matchMaterial(String.valueOf(entry.get("material")));
            final int amount = parseInt(entry.get("amount"), 1);
            if (material == null || amount <= 0) {
                plugin.getLogger().warning("Ung\u00fcltiger Item-Reward f\u00fcr Job " + job.getId() + " auf Level " + level + ": " + entry);
                return Optional.empty();
            }

            final String display = rawDisplay.isBlank() ? "<green>" + amount + "x " + formatMaterialName(material) : rawDisplay;
            return Optional.of(new PathReward(PathRewardType.ITEM, display, "", material, amount));
        }

        final String value = entry.containsKey("value") ? String.valueOf(entry.get("value")) : "";
        if (value.isBlank()) {
            plugin.getLogger().warning("Reward ohne Wert f\u00fcr Job " + job.getId() + " auf Level " + level + ": " + entry);
            return Optional.empty();
        }

        final String display = rawDisplay.isBlank()
                ? rewardType.get() == PathRewardType.COMMAND ? "<gold>Befehls-Belohnung" : "<aqua>Nachrichten-Belohnung"
                : rawDisplay;

        return Optional.of(new PathReward(rewardType.get(), display, value, null, 0));
    }

    private void grantLevelRewards(final Player player, final Job job, final int level) {
        for (final PathReward reward : getPathRewards(job, level)) {
            grantReward(player, job, level, reward);
        }
    }

    private void grantReward(final Player player, final Job job, final int level, final PathReward reward) {
        switch (reward.type()) {
            case ITEM -> grantItemReward(player, reward);
            case COMMAND -> grantCommandReward(player, job, level, reward);
            case MESSAGE -> grantMessageReward(player, job, level, reward);
        }

        if (!reward.display().isBlank() && reward.type() != PathRewardType.MESSAGE) {
            player.sendMessage(configManager.getMessage(
                    "messages.reward-unlocked",
                    Map.of(
                            "job", configManager.getJobDisplayName(job),
                            "level", String.valueOf(level),
                            "reward", reward.display()
                    ),
                    "<green>%job% Lv.%level% Belohnung: <white>%reward%</white>"
            ));
        }
    }

    private void grantItemReward(final Player player, final PathReward reward) {
        if (reward.material() == null || reward.amount() <= 0) {
            return;
        }

        final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(reward.material(), reward.amount()));
        for (final ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void grantCommandReward(final Player player, final Job job, final int level, final PathReward reward) {
        final String command = applyRewardPlaceholders(player, job, level, reward.value());
        if (command.isBlank()) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
    }

    private void grantMessageReward(final Player player, final Job job, final int level, final PathReward reward) {
        if (!reward.display().isBlank()) {
            player.sendMessage(configManager.getMessage(
                    "messages.reward-unlocked",
                    Map.of(
                            "job", configManager.getJobDisplayName(job),
                            "level", String.valueOf(level),
                            "reward", reward.display()
                    ),
                    "<green>%job% Lv.%level% Belohnung: <white>%reward%</white>"
            ));
        }

        final String message = applyRewardPlaceholders(player, job, level, reward.value());
        if (!message.isBlank()) {
            player.sendMessage(configManager.deserialize(message));
        }
    }

    private String applyRewardPlaceholders(final Player player, final Job job, final int level, final String input) {
        return input
                .replace("%player%", player.getName())
                .replace("%job%", configManager.getJobDisplayName(job))
                .replace("%level%", String.valueOf(level));
    }

    private String formatMaterialName(final Material material) {
        final String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private EntityType parseEntityType(final String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        try {
            return EntityType.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return null;
        }
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

    private void clampProgress(final JobProgress progress) {
        if (progress.getLevel() >= MAX_LEVEL) {
            progress.setLevel(MAX_LEVEL);
            progress.setXp(0L);
            progress.setFractionalXp(0.0D);
        }
    }

    private int applyExperience(
            final PlayerJobData data,
            final UUID playerUuid,
            final Player player,
            final Job job,
            final int amount,
            final boolean applyXpBoost,
            final boolean bossBarEnabled
    ) {
        if (amount <= 0) {
            return 0;
        }

        int effectiveAmount = amount;
        if (player != null) {
            final JobCoreExperienceGainEvent event = new JobCoreExperienceGainEvent(player, job, amount, !applyXpBoost);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getAmount() <= 0) {
                return 0;
            }
            effectiveAmount = event.getAmount();
        }

        final JobProgress progress = getProgress(data, job);
        if (isMaxLevel(progress.getLevel())) {
            clampProgress(progress);
            return 0;
        }

        final int previousLevel = progress.getLevel();
        final double xpBoost = applyXpBoost
                ? getUnlockedPerkValue(job, progress.getLevel(), PerkType.XP_BOOST) + getActiveXpBoosterValue(data, job)
                : 0.0D;
        final double adjusted = (effectiveAmount * (1.0D + xpBoost)) + progress.getFractionalXp();
        final int granted = Math.max(0, (int) Math.floor(adjusted));
        progress.setFractionalXp(Math.max(0.0D, adjusted - granted));

        if (granted <= 0) {
            return 0;
        }

        progress.setXp(progress.getXp() + granted);

        boolean leveledUp = false;
        final List<Integer> reachedLevels = new ArrayList<>();
        long neededXp = getXpForNextLevel(progress.getLevel());
        while (!isMaxLevel(progress.getLevel()) && neededXp > 0L && progress.getXp() >= neededXp) {
            progress.setXp(progress.getXp() - neededXp);
            progress.setLevel(progress.getLevel() + 1);
            reachedLevels.add(progress.getLevel());
            leveledUp = true;
            neededXp = getXpForNextLevel(progress.getLevel());
        }

        if (isMaxLevel(progress.getLevel())) {
            clampProgress(progress);
            neededXp = 0L;
        }

        if (player != null) {
            if (bossBarEnabled) {
                bossBarManager.showOrUpdate(player, job, progress.getLevel(), progress.getXp(), neededXp, granted);
            } else {
                bossBarManager.hide(player);
            }
        }

        if (leveledUp && player != null) {
            for (final int level : reachedLevels) {
                Bukkit.getPluginManager().callEvent(new JobCoreLevelUpEvent(player, job, Math.max(previousLevel, level - 1), level));
                grantLevelRewards(player, job, level);
            }
            bossBarManager.handleLevelUp(player, job);
            playerDataManager.savePlayerData(playerUuid);
        }

        return granted;
    }

    private double getActiveXpBoosterValue(final PlayerJobData data, final Job job) {
        data.pruneExpiredXpBoosters();
        double bonus = 0.0D;

        final XpBooster globalBooster = data.getXpBooster("global");
        if (globalBooster != null) {
            bonus += globalBooster.bonusMultiplier();
        }

        final XpBooster jobBooster = data.getXpBooster(job.getId());
        if (jobBooster != null) {
            bonus += jobBooster.bonusMultiplier();
        }

        return bonus;
    }
}
