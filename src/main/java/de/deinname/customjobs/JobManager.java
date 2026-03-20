package de.deinname.customjobs;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        configuredPerks.clear();
        configuredPathRewards.clear();
        configuredBonusDrops.clear();

        for (final Job job : Job.values()) {
            configuredXpValues.put(job, loadXpValues(job));
            configuredPerks.put(job, loadPerks(job));
            configuredPathRewards.put(job, loadPathRewards(job));
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
        final List<Integer> reachedLevels = new ArrayList<>();
        long neededXp = getXpForNextLevel(progress.getLevel());
        while (progress.getXp() >= neededXp) {
            progress.setXp(progress.getXp() - neededXp);
            progress.setLevel(progress.getLevel() + 1);
            reachedLevels.add(progress.getLevel());
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
            for (final int level : reachedLevels) {
                grantLevelRewards(player, job, level);
            }
            bossBarManager.handleLevelUp(player, job);
            playerDataManager.savePlayerData(player.getUniqueId());
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

    private Map<Integer, List<PathReward>> loadPathRewards(final Job job) {
        final ConfigurationSection section = configManager.getConfiguration()
                .getConfigurationSection("jobs." + job.getId() + ".path-rewards");

        if (section == null) {
            return job.getDefaultPathRewards();
        }

        final Map<Integer, List<PathReward>> pathRewards = new HashMap<>();
        for (final String key : section.getKeys(false)) {
            final int level = parseInt(key, -1);
            if (level <= 0) {
                plugin.getLogger().warning("Ungültige Reward-Stufe für Job " + job.getId() + ": " + key);
                continue;
            }

            final List<Map<?, ?>> rawEntries = configManager.getConfiguration()
                    .getMapList("jobs." + job.getId() + ".path-rewards." + key);

            if (rawEntries.isEmpty()) {
                continue;
            }

            final List<PathReward> rewards = new ArrayList<>();
            for (final Map<?, ?> entry : rawEntries) {
                final Optional<PathReward> reward = parsePathReward(job, level, entry);
                reward.ifPresent(rewards::add);
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

    private Optional<PathReward> parsePathReward(final Job job, final int level, final Map<?, ?> entry) {
        final Optional<PathRewardType> rewardType = PathRewardType.fromConfig(String.valueOf(entry.get("type")));
        if (rewardType.isEmpty()) {
            plugin.getLogger().warning("Ungültiger Reward-Typ für Job " + job.getId() + " auf Level " + level + ": " + entry);
            return Optional.empty();
        }

        final String rawDisplay = entry.containsKey("display") ? String.valueOf(entry.get("display")) : "";
        if (rewardType.get() == PathRewardType.ITEM) {
            final Material material = Material.matchMaterial(String.valueOf(entry.get("material")));
            final int amount = parseInt(entry.get("amount"), 1);
            if (material == null || amount <= 0) {
                plugin.getLogger().warning("Ungültiger Item-Reward für Job " + job.getId() + " auf Level " + level + ": " + entry);
                return Optional.empty();
            }

            final String display = rawDisplay.isBlank() ? "<green>" + amount + "x " + formatMaterialName(material) : rawDisplay;
            return Optional.of(new PathReward(PathRewardType.ITEM, display, "", material, amount));
        }

        final String value = entry.containsKey("value") ? String.valueOf(entry.get("value")) : "";
        if (value.isBlank()) {
            plugin.getLogger().warning("Reward ohne Wert für Job " + job.getId() + " auf Level " + level + ": " + entry);
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
                    )
            ));
        }
    }

    private void grantItemReward(final Player player, final PathReward reward) {
        if (reward.material() == null || reward.amount() <= 0) {
            return;
        }

        final Map<Integer, ItemStack> leftoverItems = player.getInventory().addItem(new ItemStack(reward.material(), reward.amount()));
        for (final ItemStack leftover : leftoverItems.values()) {
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
                    )
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
