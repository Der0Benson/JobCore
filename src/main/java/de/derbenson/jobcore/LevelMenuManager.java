package de.derbenson.jobcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LevelMenuManager implements Listener {

    private static final int SIZE = 54;
    private static final int CLOSE_SLOT = 53;
    private static final int OVERVIEW_HEADER_SLOT = 4;
    private static final int OVERVIEW_INFO_SLOT = 22;
    private static final int OVERVIEW_LEADERBOARD_SLOT = 40;
    private static final List<Integer> OVERVIEW_JOB_SLOTS = List.of(10, 13, 16, 28, 31, 34);
    private static final int PATH_LEADERBOARD_SLOT = 50;
    private static final List<Integer> PATH_NODE_SLOTS = List.of(
            27, 28, 19, 10,
            11, 12,
            21, 30,
            31, 32,
            23, 14,
            15, 16,
            25, 34,
            35
    );
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 47;
    private static final int SUMMARY_SLOT = 49;
    private static final int NEXT_SLOT = 51;
    private static final int PATH_PAGE_SIZE = PATH_NODE_SLOTS.size();
    private static final int LEADERBOARD_HEADER_SLOT = 4;
    private static final int LEADERBOARD_BACK_SLOT = 45;
    private static final int LEADERBOARD_SUMMARY_SLOT = 49;
    private static final List<Integer> LEADERBOARD_JOB_SLOTS = List.of(10, 13, 16, 28, 31, 34);
    private static final int LEADERBOARD_QUEST_SLOT = 22;

    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final LeaderboardManager leaderboardManager;
    private final DecimalFormat percentFormat = new DecimalFormat("0.##%");

    public LevelMenuManager(
            final ConfigManager configManager,
            final JobManager jobManager,
            final LeaderboardManager leaderboardManager
    ) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.leaderboardManager = leaderboardManager;
    }

    public void openOverview(final Player player) {
        final LevelMenuHolder holder = new LevelMenuHolder(LevelMenuView.OVERVIEW, null, 0);
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, configManager.getLevelOverviewTitle());
        holder.setInventory(inventory);

        fillOverviewBackground(inventory);
        inventory.setItem(OVERVIEW_HEADER_SLOT, createItem(
                Material.BOOK,
                menuString("overview-header-title", "<gold>Job-Auswahl</gold>"),
                menuList("overview-header-lore", List.of(
                        "<gray>Wahle einen Job, dessen Pfad du genauer sehen mochtest.",
                        "<gray><white>Holzfaller</white>, <white>Miner</white>, <white>Farmer</white>, <white>Krieger</white>, <white>Angler</white> und <white>Alchemist</white> sind spielbar.",
                        "<gray>Weitere Jobs sind bereits als Platzhalter vorbereitet."
                )),
                false
        ));
        placeOverviewSummary(player, inventory);
        placeOverviewJobs(player, inventory);
        placeOverviewLeaderboardButton(inventory);
        placeCloseButton(inventory);

        player.openInventory(inventory);
    }

    public void openLeaderboard(final Player player) {
        if (!hasLeaderboardPermission(player)) {
            player.sendMessage(configManager.getMessage("messages.no-permission"));
            return;
        }

        final LevelMenuHolder holder = new LevelMenuHolder(LevelMenuView.LEADERBOARD, null, 0);
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, menuString("leaderboard-title", "Bestenlisten"));
        holder.setInventory(inventory);

        fillOverviewBackground(inventory);
        placeLeaderboardHeader(inventory);
        placeLeaderboardEntries(inventory);
        placeLeaderboardFooter(inventory);
        placeCloseButton(inventory);

        player.openInventory(inventory);
    }

    public void openPath(final Player player, final Job job, final int requestedPage) {
        final int maxLevel = getMaxDisplayLevel();
        final int totalPages = getTotalPages(maxLevel);
        final int page = clampPage(requestedPage, totalPages);
        final LevelMenuHolder holder = new LevelMenuHolder(LevelMenuView.PATH, job, page);
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, configManager.getLevelPathTitle());
        holder.setInventory(inventory);

        fillPathBackground(inventory);
        placePathGrid(player, inventory, job, page, maxLevel);
        placePathNavigation(player, inventory, job, page, totalPages, maxLevel);
        placeCloseButton(inventory);

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LevelMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        if (holder.getView() == LevelMenuView.OVERVIEW) {
            handleOverviewClick(player, slot);
            return;
        }

        if (holder.getView() == LevelMenuView.LEADERBOARD) {
            handleLeaderboardClick(player, slot);
            return;
        }

        handlePathClick(player, holder, slot);
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LevelMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleOverviewClick(final Player player, final int slot) {
        if (slot == OVERVIEW_LEADERBOARD_SLOT) {
            if (!hasLeaderboardPermission(player)) {
                player.sendMessage(configManager.getMessage("messages.no-permission"));
                return;
            }
            openLeaderboard(player);
            return;
        }

        final List<Job> jobs = visibleJobs();
        final List<DummyJobCard> dummyJobs = getDummyJobs();
        for (int index = 0; index < jobs.size() && index < OVERVIEW_JOB_SLOTS.size(); index++) {
            if (slot == OVERVIEW_JOB_SLOTS.get(index)) {
                openPath(player, jobs.get(index), 0);
                return;
            }
        }

        for (int index = jobs.size(); index < OVERVIEW_JOB_SLOTS.size() && (index - jobs.size()) < dummyJobs.size(); index++) {
            if (slot == OVERVIEW_JOB_SLOTS.get(index)) {
                final DummyJobCard dummyJobCard = dummyJobs.get(index - jobs.size());
                player.sendMessage(configManager.getChatMessage(dummyJobCard.clickMessage(), Map.of("job", dummyJobCard.displayName())));
                return;
            }
        }
    }

    private void handlePathClick(final Player player, final LevelMenuHolder holder, final int slot) {
        final Job job = holder.getSelectedJob();
        if (job == null) {
            openOverview(player);
            return;
        }

        final int totalPages = getTotalPages(getMaxDisplayLevel());
        if (slot == BACK_SLOT) {
            openOverview(player);
            return;
        }
        if (slot == PATH_LEADERBOARD_SLOT) {
            if (!hasLeaderboardPermission(player)) {
                player.sendMessage(configManager.getMessage("messages.no-permission"));
                return;
            }
            openLeaderboard(player);
            return;
        }
        if (slot == PREVIOUS_SLOT && holder.getPage() > 0) {
            openPath(player, job, holder.getPage() - 1);
            return;
        }
        if (slot == NEXT_SLOT && holder.getPage() < (totalPages - 1)) {
            openPath(player, job, holder.getPage() + 1);
        }
    }

    private void handleLeaderboardClick(final Player player, final int slot) {
        if (slot == LEADERBOARD_BACK_SLOT) {
            openOverview(player);
            return;
        }

        final List<Job> jobs = visibleJobs();
        for (int index = 0; index < jobs.size() && index < LEADERBOARD_JOB_SLOTS.size(); index++) {
            if (slot == LEADERBOARD_JOB_SLOTS.get(index)) {
                openPath(player, jobs.get(index), 0);
                return;
            }
        }
    }

    private void fillOverviewBackground(final Inventory inventory) {
        final ItemStack frame = createItemStack(Material.BLACK_STAINED_GLASS_PANE, "<gray>", List.of(), false);
        final ItemStack fill = createItemStack(Material.GRAY_STAINED_GLASS_PANE, "<gray>", List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slot / 9 == 0 || slot / 9 == 5 ? frame : fill);
        }
    }

    private void fillPathBackground(final Inventory inventory) {
        final ItemStack pathBackground = createItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>", List.of(), false);
        final ItemStack navigationBackground = createItemStack(Material.BLACK_STAINED_GLASS_PANE, "<gray>", List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slot < 45 ? pathBackground : navigationBackground);
        }
    }

    private void placeOverviewSummary(final Player player, final Inventory inventory) {
        int totalLevel = 0;
        Job highestJob = null;
        int highestLevel = -1;

        for (final Job job : jobManager.getJobs()) {
            final int level = jobManager.getProgress(player.getUniqueId(), job).getLevel();
            totalLevel += level;
            if (level > highestLevel) {
                highestLevel = level;
                highestJob = job;
            }
        }

        inventory.setItem(OVERVIEW_INFO_SLOT, createItem(
                Material.NETHER_STAR,
                menuString("overview-summary-title", "<green>Dein Fortschritt</green>"),
                formatList(
                        menuList("overview-summary-lore", List.of(
                                "<gray>Gesamtlevel: <white>%totalLevel%</white>",
                                "<gray>Verfugbare Pfade: <white>%jobCount%</white>",
                                "<gray>Hochster Job: <white>%highestJob%</white>",
                                "<yellow>Klicke auf einen Job, um die Detailansicht zu offnen."
                        )),
                        Map.of(
                                "totalLevel", String.valueOf(totalLevel),
                                "jobCount", String.valueOf(jobManager.getJobs().size()),
                                "highestJob", highestJob == null
                                        ? menuString("overview-summary-no-job", "Noch keiner")
                                        : configManager.getJobDisplayName(highestJob)
                        )
                ),
                false
        ));
    }

    private void placeOverviewJobs(final Player player, final Inventory inventory) {
        int index = 0;
        for (final Job job : visibleJobs()) {
            inventory.setItem(OVERVIEW_JOB_SLOTS.get(index++), createRealJobItem(player, job));
        }

        final List<DummyJobCard> dummyJobs = getDummyJobs();
        for (final DummyJobCard dummyJobCard : dummyJobs) {
            if (index >= OVERVIEW_JOB_SLOTS.size()) {
                break;
            }
            inventory.setItem(OVERVIEW_JOB_SLOTS.get(index++), createDummyJobItem(dummyJobCard));
        }
    }

    private void placeOverviewLeaderboardButton(final Inventory inventory) {
        inventory.setItem(OVERVIEW_LEADERBOARD_SLOT, createItem(
                Material.TOTEM_OF_UNDYING,
                menuString("overview-leaderboard-title", "<gold>Bestenlisten</gold>"),
                menuList("overview-leaderboard-lore", List.of(
                        "<gray>Sieh dir die starksten Spieler pro Job an.",
                        "<gray>Ausserdem findest du hier die aktivsten Questspieler.",
                        "<yellow>Klicke, um die Bestenlisten zu offnen."
                )),
                true
        ));
    }

    private void placePathGrid(final Player player, final Inventory inventory, final Job job, final int page, final int maxLevel) {
        final JobProgress progress = jobManager.getProgress(player.getUniqueId(), job);

        for (int index = 0; index < PATH_NODE_SLOTS.size(); index++) {
            final int level = (page * PATH_PAGE_SIZE) + index + 1;
            final int slot = PATH_NODE_SLOTS.get(index);
            if (level > maxLevel) {
                inventory.setItem(slot, createItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>", List.of(), false));
                continue;
            }

            inventory.setItem(slot, createLevelNode(job, level, progress));
        }
    }

    private void placePathNavigation(
            final Player player,
            final Inventory inventory,
            final Job job,
            final int page,
            final int totalPages,
            final int maxLevel
    ) {
        final JobProgress progress = jobManager.getProgress(player.getUniqueId(), job);
        final long neededXp = jobManager.getXpForNextLevel(progress.getLevel());
        final int pageStartLevel = (page * PATH_PAGE_SIZE) + 1;
        final int pageEndLevel = Math.min(maxLevel, pageStartLevel + PATH_PAGE_SIZE - 1);
        final JobPerk nextPerk = nextPerk(job, progress.getLevel());
        final Integer nextRewardLevel = nextRewardLevel(job, progress.getLevel());
        final List<PathReward> nextRewards = nextRewardLevel == null ? List.of() : jobManager.getPathRewards(job, nextRewardLevel);

        inventory.setItem(BACK_SLOT, createItem(
                Material.BOOK,
                configManager.getLevelMenuBackLabel(),
                menuList("path-back-lore", List.of("<gray>Zur Startseite mit allen Jobs zuruckkehren.")),
                false
        ));
        inventory.setItem(PREVIOUS_SLOT, createNavigationItem(
                page > 0,
                Material.ARROW,
                configManager.getLevelMenuPreviousPageLabel(),
                page > 0
                        ? menuString("path-previous-enabled-lore", "<gray>Offnet die vorige Pfad-Seite.</gray>")
                        : menuString("path-previous-disabled-lore", "<dark_gray>Du bist bereits auf der ersten Seite.</dark_gray>")
        ));
        inventory.setItem(SUMMARY_SLOT, createItem(
                configManager.getJobIcon(job),
                "<green>" + configManager.getJobDisplayName(job) + "</green>",
                buildSummaryLore(job, progress, neededXp, nextPerk, nextRewardLevel, nextRewards, page + 1, totalPages, pageStartLevel, pageEndLevel),
                true
        ));
        inventory.setItem(NEXT_SLOT, createNavigationItem(
                page < (totalPages - 1),
                Material.SPECTRAL_ARROW,
                configManager.getLevelMenuNextPageLabel(),
                page < (totalPages - 1)
                        ? menuString("path-next-enabled-lore", "<gray>Offnet die nachste Pfad-Seite.</gray>")
                        : menuString("path-next-disabled-lore", "<dark_gray>Dies ist bereits die letzte Seite.</dark_gray>")
        ));
        inventory.setItem(PATH_LEADERBOARD_SLOT, createItem(
                Material.TOTEM_OF_UNDYING,
                menuString("path-leaderboard-title", "<gold>Bestenlisten</gold>"),
                menuList("path-leaderboard-lore", List.of("<gray>Offnet die globalen Job- und Quest-Rankings.</gray>")),
                true
        ));
    }

    private void placeLeaderboardHeader(final Inventory inventory) {
        inventory.setItem(LEADERBOARD_HEADER_SLOT, createItem(
                Material.TOTEM_OF_UNDYING,
                menuString("leaderboard-header-title", "<gold>Bestenlisten</gold>"),
                menuList("leaderboard-header-lore", List.of(
                        "<gray>Hier siehst du die starksten Spieler pro Job.",
                        "<gray>In der Mitte findest du ausserdem die aktivsten Questspieler."
                )),
                true
        ));
    }

    private void placeLeaderboardEntries(final Inventory inventory) {
        final List<Job> jobs = visibleJobs();
        for (int index = 0; index < jobs.size() && index < LEADERBOARD_JOB_SLOTS.size(); index++) {
            final Job job = jobs.get(index);
            inventory.setItem(LEADERBOARD_JOB_SLOTS.get(index), createJobLeaderboardItem(job));
        }

        inventory.setItem(LEADERBOARD_QUEST_SLOT, createQuestLeaderboardItem());
    }

    private void placeLeaderboardFooter(final Inventory inventory) {
        inventory.setItem(LEADERBOARD_BACK_SLOT, createItem(
                Material.BOOK,
                configManager.getLevelMenuBackLabel(),
                menuList("leaderboard-back-lore", List.of("<gray>Zuruck zur Job-Auswahl.</gray>")),
                false
        ));
        inventory.setItem(LEADERBOARD_SUMMARY_SLOT, createItem(
                Material.NETHER_STAR,
                menuString("leaderboard-summary-title", "<green>Ranking-Info</green>"),
                menuList("leaderboard-summary-lore", List.of(
                        "<gray>Job-Rankings sortieren nach <white>Level</white> und dann <white>XP</white>.",
                        "<gray>Quest-Ranking sortiert nach <white>abgegebenen Missionen</white>.",
                        "<yellow>Klicke auf einen Job, um direkt seinen Pfad zu offnen."
                )),
                false
        ));
    }

    private List<String> buildSummaryLore(
            final Job job,
            final JobProgress progress,
            final long neededXp,
            final JobPerk nextPerk,
            final Integer nextRewardLevel,
            final List<PathReward> nextRewards,
            final int currentPage,
            final int totalPages,
            final int pageStartLevel,
            final int pageEndLevel
    ) {
        final List<String> lore = new ArrayList<>();
        lore.add(format(menuString("path-summary-level", "<gray>Level: <white>%level%</white>"), Map.of(
                "level", String.valueOf(progress.getLevel())
        )));
        lore.add(jobManager.isMaxLevel(progress.getLevel())
                ? menuString("path-summary-xp-max", "<gray>XP: <white>MAX/MAX</white>")
                : format(menuString("path-summary-xp", "<gray>XP: <white>%xp%/%needed%</white>"), Map.of(
                "xp", String.valueOf(progress.getXp()),
                "needed", String.valueOf(neededXp)
        )));
        lore.add(format(menuString("path-summary-xp-boost", "<gray>Aktive XP-Boni: <white>%boost%</white>"), Map.of(
                "boost", percentFormat.format(jobManager.getUnlockedPerkValue(job, progress.getLevel(), PerkType.XP_BOOST))
        )));
        lore.add(format(menuString("path-summary-page", "<gray>Seite: <white>%page%/%pages%</white>"), Map.of(
                "page", String.valueOf(currentPage),
                "pages", String.valueOf(totalPages)
        )));
        lore.add(format(menuString("path-summary-range", "<gray>Bereich: <white>Lv.%start% bis Lv.%end%</white>"), Map.of(
                "start", String.valueOf(pageStartLevel),
                "end", String.valueOf(pageEndLevel)
        )));
        if (nextPerk == null) {
            lore.add(menuString("path-summary-next-perk-none", "<green>Alle aktuellen Standard-Perks sind freigeschaltet."));
        } else {
            lore.add(format(menuString("path-summary-next-perk", "<gray>Nachster Perk: <white>Lv.%level%</white>"), Map.of(
                    "level", String.valueOf(nextPerk.level())
            )));
            lore.add(nextPerk.display());
        }
        if (nextRewardLevel == null || nextRewards.isEmpty()) {
            lore.add(menuString("path-summary-next-reward-none", "<green>Keine weitere Pfad-Belohnung definiert."));
        } else {
            lore.add(format(menuString("path-summary-next-reward", "<gray>Nachste Belohnung: <white>Lv.%level%</white>"), Map.of(
                    "level", String.valueOf(nextRewardLevel)
            )));
            lore.add(nextRewards.getFirst().display());
            if (nextRewards.size() > 1) {
                lore.add(format(menuString("path-summary-more-rewards", "<gray>+<white>%count%</white> weitere Belohnungen"), Map.of(
                        "count", String.valueOf(nextRewards.size() - 1)
                )));
            }
        }
        return lore;
    }

    private ItemStack createLevelNode(final Job job, final int level, final JobProgress progress) {
        final int currentLevel = progress.getLevel();
        final boolean unlocked = currentLevel >= level;
        final boolean next = !unlocked && level == (currentLevel + 1);
        final List<JobPerk> perks = perksAtLevel(job, level);
        final List<PathReward> rewards = jobManager.getPathRewards(job, level);
        final boolean specialLevel = !perks.isEmpty() || !rewards.isEmpty();

        final String stateText = unlocked
                ? menuString("level-node-state-unlocked", "<green>Freigeschaltet")
                : next
                ? menuString("level-node-state-next", "<yellow>Nachstes Ziel")
                : menuString("level-node-state-locked", "<gray>Gesperrt");
        final List<String> lore = new ArrayList<>();
        lore.add(stateText);
        if (next) {
            final long missingXp = Math.max(0L, jobManager.getXpForNextLevel(currentLevel) - progress.getXp());
            lore.add(format(menuString("level-node-missing-xp", "<gray>Fehlende XP: <white>%xp%</white>"), Map.of(
                    "xp", String.valueOf(missingXp)
            )));
        }

        if (perks.isEmpty() && rewards.isEmpty()) {
            lore.add(menuString("level-node-no-reward", "<dark_gray>Keine neue Belohnung auf dieser Stufe."));
        }

        if (!perks.isEmpty()) {
            lore.add(menuString("level-node-perks-label", "<gray>Perks:"));
            for (final JobPerk perk : perks) {
                lore.add(perk.display());
            }
        }

        if (!rewards.isEmpty()) {
            lore.add(menuString("level-node-rewards-label", "<gray>Belohnungen:"));
            for (final PathReward reward : rewards) {
                lore.add(reward.display());
            }
        }

        return createItem(
                getNodeMaterial(unlocked, specialLevel),
                format(menuString("level-node-title", "%color%Level %level%"), Map.of(
                        "color", unlocked ? "<green>" : next ? "<yellow>" : "<gray>",
                        "level", String.valueOf(level)
                )),
                lore,
                specialLevel
        );
    }

    private Material getNodeMaterial(final boolean unlocked, final boolean perkLevel) {
        if (perkLevel) {
            return Material.YELLOW_STAINED_GLASS;
        }
        if (unlocked) {
            return Material.LIME_STAINED_GLASS_PANE;
        }
        return Material.RED_STAINED_GLASS_PANE;
    }

    private List<JobPerk> perksAtLevel(final Job job, final int level) {
        return jobManager.getPerks(job).stream()
                .filter(perk -> perk.level() == level)
                .toList();
    }

    private int getMaxDisplayLevel() {
        return jobManager.getMaxLevel();
    }

    private int getTotalPages(final int maxLevel) {
        return Math.max(1, (int) Math.ceil(maxLevel / (double) PATH_PAGE_SIZE));
    }

    private int clampPage(final int requestedPage, final int totalPages) {
        if (requestedPage < 0) {
            return 0;
        }
        return Math.min(requestedPage, totalPages - 1);
    }

    private JobPerk nextPerk(final Job job, final int currentLevel) {
        return jobManager.getPerks(job).stream()
                .filter(perk -> perk.level() > currentLevel)
                .findFirst()
                .orElse(null);
    }

    private Integer nextRewardLevel(final Job job, final int currentLevel) {
        return java.util.stream.IntStream.rangeClosed(currentLevel + 1, jobManager.getMaxLevel())
                .filter(level -> !jobManager.getPathRewards(job, level).isEmpty())
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private ItemStack createRealJobItem(final Player player, final Job job) {
        final JobProgress progress = jobManager.getProgress(player.getUniqueId(), job);
        final long neededXp = jobManager.getXpForNextLevel(progress.getLevel());
        final String progressText = jobManager.isMaxLevel(progress.getLevel())
                ? menuString("path-summary-xp-max", "<gray>XP: <white>MAX/MAX</white>")
                : format(menuString("path-summary-xp", "<gray>XP: <white>%xp%/%needed%</white>"), Map.of(
                "xp", String.valueOf(progress.getXp()),
                "needed", String.valueOf(neededXp)
        ));
        final long unlockedPerks = jobManager.getPerks(job).stream()
                .filter(perk -> progress.getLevel() >= perk.level())
                .count();
        final long unlockedRewards = java.util.stream.IntStream.rangeClosed(1, jobManager.getMaxLevel())
                .filter(level -> progress.getLevel() >= level)
                .filter(level -> !jobManager.getPathRewards(job, level).isEmpty())
                .count();

        return createItem(
                configManager.getJobIcon(job),
                "<green>" + configManager.getJobDisplayName(job) + "</green>",
                formatList(
                        menuList("real-job-lore", List.of(
                                "<gray>Level: <white>%level%</white>",
                                "%xpLine%",
                                "<gray>Freigeschaltete Perks: <white>%unlockedPerks%/%totalPerks%</white>",
                                "<gray>Belohnungs-Stufen: <white>%rewardLevels%</white>",
                                "%statusLine%",
                                "%clickLine%"
                        )),
                        Map.of(
                                "level", String.valueOf(progress.getLevel()),
                                "xpLine", progressText,
                                "unlockedPerks", String.valueOf(unlockedPerks),
                                "totalPerks", String.valueOf(jobManager.getPerks(job).size()),
                                "rewardLevels", String.valueOf(unlockedRewards),
                                "statusLine", menuString("real-job-status", "<green>Verfugbar"),
                                "clickLine", menuString("real-job-click-lore", "<yellow>Klicke fur den detaillierten Pfad.")
                        )
                ),
                true
        );
    }

    private ItemStack createJobLeaderboardItem(final Job job) {
        final List<LeaderboardManager.JobLeaderboardEntry> topEntries = leaderboardManager.getTopPlayersForJob(job, 5);
        final List<String> lore = new ArrayList<>();

        if (topEntries.isEmpty()) {
            lore.add(leaderboardManager.isRefreshing()
                    ? menuString("leaderboard-job-refreshing", "<gray>Ranking wird gerade aktualisiert...</gray>")
                    : menuString("leaderboard-job-empty", "<dark_gray>Noch keine Eintrage vorhanden."));
        } else {
            int rank = 1;
            for (final LeaderboardManager.JobLeaderboardEntry entry : topEntries) {
                lore.add(format(menuString("leaderboard-job-entry", "<gray>#%rank% <white>%player%</white> <gray>. Lv.<white>%level%</white> <gray>. <white>%xp% XP</white>"), Map.of(
                        "rank", String.valueOf(rank++),
                        "player", entry.playerName(),
                        "level", String.valueOf(entry.level()),
                        "xp", String.valueOf(entry.xp())
                )));
            }
        }

        lore.add(menuString("leaderboard-job-click-lore", "<yellow>Klicke, um den Pfad zu offnen."));

        return createItem(
                configManager.getJobIcon(job),
                "<green>" + configManager.getJobDisplayName(job) + "</green>",
                lore,
                true
        );
    }

    private ItemStack createQuestLeaderboardItem() {
        final List<LeaderboardManager.QuestLeaderboardEntry> topEntries = leaderboardManager.getTopQuestPlayers(5);
        final List<String> lore = new ArrayList<>();

        if (topEntries.isEmpty()) {
            lore.add(leaderboardManager.isRefreshing()
                    ? menuString("quest-leaderboard-refreshing", "<gray>Ranking wird gerade aktualisiert...</gray>")
                    : menuString("quest-leaderboard-empty", "<dark_gray>Noch keine Quest-Abgaben vorhanden."));
        } else {
            int rank = 1;
            for (final LeaderboardManager.QuestLeaderboardEntry entry : topEntries) {
                lore.add(format(menuString("quest-leaderboard-entry", "<gray>#%rank% <white>%player%</white> <gray>. <white>%count%</white> abgeschlossene Missionen"), Map.of(
                        "rank", String.valueOf(rank++),
                        "player", entry.playerName(),
                        "count", String.valueOf(entry.totalClaims())
                )));
            }
        }

        return createItem(
                Material.WRITABLE_BOOK,
                menuString("quest-leaderboard-title", "<light_purple>Questspieler</light_purple>"),
                lore,
                true
        );
    }

    private ItemStack createDummyJobItem(final DummyJobCard dummyJobCard) {
        return createItem(
                dummyJobCard.icon(),
                "<gray>" + dummyJobCard.displayName() + "</gray>",
                formatList(
                        menuList("dummy-job-lore", List.of(
                                "<gray>%description%</gray>",
                                "<dark_gray>Demnachst verfugbar",
                                "<dark_gray>Noch kein spielbarer Pfad hinterlegt."
                        )),
                        Map.of(
                                "job", dummyJobCard.displayName(),
                                "description", dummyJobCard.description()
                        )
                ),
                false
        );
    }

    private List<Job> visibleJobs() {
        final List<Job> jobs = jobManager.getJobs();
        if (jobs.size() <= OVERVIEW_JOB_SLOTS.size()) {
            return jobs;
        }
        return jobs.subList(0, OVERVIEW_JOB_SLOTS.size());
    }

    private void placeCloseButton(final Inventory inventory) {
        inventory.setItem(CLOSE_SLOT, createItem(
                Material.BARRIER,
                configManager.getLevelMenuCloseLabel(),
                menuList("close-lore", List.of("<gray>Schliesst dieses Menu.</gray>")),
                false
        ));
    }

    private ItemStack createNavigationItem(
            final boolean enabled,
            final Material enabledMaterial,
            final String title,
            final String lore
    ) {
        return createItem(
                enabled ? enabledMaterial : Material.GRAY_DYE,
                enabled ? title : menuString("navigation-disabled-title", "<gray>Ausgegraut</gray>"),
                List.of(lore),
                false
        );
    }

    private ItemStack createItem(
            final Material material,
            final String title,
            final List<String> lore,
            final boolean glowing
    ) {
        return createItemStack(material, title, lore, glowing);
    }

    private ItemStack createItemStack(
            final Material material,
            final String title,
            final List<String> lore,
            final boolean glowing
    ) {
        final List<Component> lines = lore.stream()
                .map(configManager::deserialize)
                .map(this::withoutItalic)
                .toList();
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(withoutItalic(configManager.deserialize(title)));
        itemMeta.lore(lines);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glowing) {
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private String menuString(final String path, final String fallback) {
        return configManager.getConfiguration().getString("menu." + path, fallback);
    }

    private List<String> menuList(final String path, final List<String> fallback) {
        final List<String> configured = configManager.getConfiguration().getStringList("menu." + path);
        return configured.isEmpty() ? fallback : configured;
    }

    private String format(final String template, final Map<String, String> placeholders) {
        String result = template;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return result;
    }

    private List<String> formatList(final List<String> templates, final Map<String, String> placeholders) {
        final List<String> lines = new ArrayList<>(templates.size());
        for (final String template : templates) {
            lines.add(format(template, placeholders));
        }
        return lines;
    }

    private List<DummyJobCard> getDummyJobs() {
        final ConfigurationSection section = configManager.getConfiguration().getConfigurationSection("menu.dummy-jobs");
        final String defaultClickMessage = menuString("dummy-job-click-message", "<yellow>%job% ist noch ein Platzhalter und wird spater freigeschaltet.</yellow>");
        if (section == null) {
            return List.of(new DummyJobCard("Jager", Material.BOW, "Mobs, Beute und Jagdserien.", defaultClickMessage));
        }

        final List<DummyJobCard> cards = new ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection cardSection = section.getConfigurationSection(key);
            if (cardSection == null) {
                continue;
            }

            final Material icon = Material.matchMaterial(cardSection.getString("icon", "BARRIER"));
            cards.add(new DummyJobCard(
                    cardSection.getString("display-name", key),
                    icon == null ? Material.BARRIER : icon,
                    cardSection.getString("description", ""),
                    cardSection.getString("click-message", defaultClickMessage)
            ));
        }

        return cards.isEmpty()
                ? List.of(new DummyJobCard("Jager", Material.BOW, "Mobs, Beute und Jagdserien.", defaultClickMessage))
                : List.copyOf(cards);
    }

    private Component withoutItalic(final Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private boolean hasLeaderboardPermission(final Player player) {
        return player.hasPermission("jobcore.menu.leaderboard") || player.hasPermission("jobcore.admin");
    }

    private record DummyJobCard(String displayName, Material icon, String description, String clickMessage) {
    }
}
