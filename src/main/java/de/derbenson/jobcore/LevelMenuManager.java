package de.derbenson.jobcore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public final class LevelMenuManager implements Listener {

    private static final int SIZE = 54;
    private static final int CLOSE_SLOT = 53;
    private static final int OVERVIEW_HEADER_SLOT = 4;
    private static final int OVERVIEW_INFO_SLOT = 22;
    private static final List<Integer> OVERVIEW_JOB_SLOTS = List.of(10, 13, 16, 28, 31, 34);
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
    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final DecimalFormat percentFormat = new DecimalFormat("0.##%");
    private final List<DummyJobCard> dummyJobs = List.of(
            new DummyJobCard("Fischer", Material.FISHING_ROD, "Seen, Ozeane und seltene F\u00e4nge."),
            new DummyJobCard("J\u00e4ger", Material.BOW, "Mobs, Beute und Jagdserien."),
            new DummyJobCard("Alchemist", Material.BREWING_STAND, "Zutaten, Tr\u00e4nke und Experimente.")
    );

    
    public LevelMenuManager(final ConfigManager configManager, final JobManager jobManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
    }

    
    public void openOverview(final Player player) {
        final LevelMenuHolder holder = new LevelMenuHolder(LevelMenuView.OVERVIEW, null, 0);
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, configManager.getLevelOverviewTitle());
        holder.setInventory(inventory);

        fillOverviewBackground(inventory);
        inventory.setItem(OVERVIEW_HEADER_SLOT, createItem(Material.BOOK, "<gold>Job-Auswahl</gold>", List.of(
                "<gray>W\u00e4hle einen Job, dessen Pfad du genauer sehen m\u00f6chtest.",
                "<gray><white>Holzf\u00e4ller</white>, <white>Miner</white> und <white>Farmer</white> sind spielbar.",
                "<gray>Weitere Jobs sind bereits als Platzhalter vorbereitet."
        ), false));
        placeOverviewSummary(player, inventory);
        placeOverviewJobs(player, inventory);
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

        handlePathClick(player, holder, slot);
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LevelMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleOverviewClick(final Player player, final int slot) {
        final List<Job> jobs = visibleJobs();
        for (int index = 0; index < jobs.size() && index < OVERVIEW_JOB_SLOTS.size(); index++) {
            if (slot == OVERVIEW_JOB_SLOTS.get(index)) {
                openPath(player, jobs.get(index), 0);
                return;
            }
        }

        for (int index = jobs.size(); index < OVERVIEW_JOB_SLOTS.size() && (index - jobs.size()) < dummyJobs.size(); index++) {
            if (slot == OVERVIEW_JOB_SLOTS.get(index)) {
                final DummyJobCard dummyJobCard = dummyJobs.get(index - jobs.size());
                player.sendMessage(configManager.deserialize(
                        "<yellow>%job% ist noch ein Platzhalter und wird sp\u00e4ter freigeschaltet.</yellow>",
                        java.util.Map.of("job", dummyJobCard.displayName())
                ));
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
        if (slot == PREVIOUS_SLOT && holder.getPage() > 0) {
            openPath(player, job, holder.getPage() - 1);
            return;
        }
        if (slot == NEXT_SLOT && holder.getPage() < (totalPages - 1)) {
            openPath(player, job, holder.getPage() + 1);
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

        inventory.setItem(OVERVIEW_INFO_SLOT, createItem(Material.NETHER_STAR, "<green>Dein Fortschritt</green>", List.of(
                "<gray>Gesamtlevel: <white>" + totalLevel + "</white>",
                "<gray>Verf\u00fcgbare Pfade: <white>" + jobManager.getJobs().size() + "</white>",
                "<gray>H\u00f6chster Job: <white>" + (highestJob == null ? "Noch keiner" : configManager.getJobDisplayName(highestJob)) + "</white>",
                "<yellow>Klicke auf einen Job, um die Detailansicht zu \u00f6ffnen."
        ), false));
    }

    private void placeOverviewJobs(final Player player, final Inventory inventory) {
        int index = 0;
        for (final Job job : visibleJobs()) {
            inventory.setItem(OVERVIEW_JOB_SLOTS.get(index++), createRealJobItem(player, job));
        }

        for (final DummyJobCard dummyJobCard : dummyJobs) {
            if (index >= OVERVIEW_JOB_SLOTS.size()) {
                break;
            }
            inventory.setItem(OVERVIEW_JOB_SLOTS.get(index++), createDummyJobItem(dummyJobCard));
        }
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
                List.of("<gray>Zur Startseite mit allen Jobs zur\u00fcckkehren."),
                false
        ));
        inventory.setItem(PREVIOUS_SLOT, createNavigationItem(
                page > 0,
                Material.ARROW,
                configManager.getLevelMenuPreviousPageLabel(),
                page > 0 ? "<gray>\u00d6ffnet die vorige Pfad-Seite.</gray>" : "<dark_gray>Du bist bereits auf der ersten Seite.</dark_gray>"
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
                page < (totalPages - 1) ? "<gray>\u00d6ffnet die n\u00e4chste Pfad-Seite.</gray>" : "<dark_gray>Dies ist bereits die letzte Seite.</dark_gray>"
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
        lore.add("<gray>Level: <white>" + progress.getLevel() + "</white>");
        lore.add(jobManager.isMaxLevel(progress.getLevel())
                ? "<gray>XP: <white>MAX/MAX</white>"
                : "<gray>XP: <white>" + progress.getXp() + "/" + neededXp + "</white>");
        lore.add("<gray>Aktive XP-Boni: <white>" + percentFormat.format(jobManager.getUnlockedPerkValue(job, progress.getLevel(), PerkType.XP_BOOST)) + "</white>");
        lore.add("<gray>Seite: <white>" + currentPage + "/" + totalPages + "</white>");
        lore.add("<gray>Bereich: <white>Lv." + pageStartLevel + " bis Lv." + pageEndLevel + "</white>");
        if (nextPerk == null) {
            lore.add("<green>Alle aktuellen Standard-Perks sind freigeschaltet.");
        } else {
            lore.add("<gray>N\u00e4chster Perk: <white>Lv." + nextPerk.level() + "</white>");
            lore.add(nextPerk.display());
        }
        if (nextRewardLevel == null || nextRewards.isEmpty()) {
            lore.add("<green>Keine weitere Pfad-Belohnung definiert.");
        } else {
            lore.add("<gray>N\u00e4chste Belohnung: <white>Lv." + nextRewardLevel + "</white>");
            lore.add(nextRewards.getFirst().display());
            if (nextRewards.size() > 1) {
                lore.add("<gray>+<white>" + (nextRewards.size() - 1) + "</white> weitere Belohnungen");
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

        final String stateText = unlocked ? "<green>Freigeschaltet" : next ? "<yellow>N\u00e4chstes Ziel" : "<gray>Gesperrt";
        final List<String> lore = new ArrayList<>();
        lore.add(stateText);
        if (next) {
            final long missingXp = Math.max(0L, jobManager.getXpForNextLevel(currentLevel) - progress.getXp());
            lore.add("<gray>Fehlende XP: <white>" + missingXp + "</white>");
        }

        if (perks.isEmpty() && rewards.isEmpty()) {
            lore.add("<dark_gray>Keine neue Belohnung auf dieser Stufe.");
        }

        if (!perks.isEmpty()) {
            lore.add("<gray>Perks:");
            for (final JobPerk perk : perks) {
                lore.add(perk.display());
            }
        }

        if (!rewards.isEmpty()) {
            lore.add("<gray>Belohnungen:");
            for (final PathReward reward : rewards) {
                lore.add(reward.display());
            }
        }

        return createItem(
                getNodeMaterial(unlocked, specialLevel),
                (unlocked ? "<green>" : next ? "<yellow>" : "<gray>") + "Level " + level,
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
                ? "<gray>XP: <white>MAX/MAX</white>"
                : "<gray>XP: <white>" + progress.getXp() + "/" + neededXp + "</white>";
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
                List.of(
                        "<gray>Level: <white>" + progress.getLevel() + "</white>",
                        progressText,
                        "<gray>Freigeschaltete Perks: <white>" + unlockedPerks + "/" + jobManager.getPerks(job).size() + "</white>",
                        "<gray>Belohnungs-Stufen: <white>" + unlockedRewards + "</white>",
                        "<green>Verf\u00fcgbar",
                        "<yellow>Klicke f\u00fcr den detaillierten Pfad."
                ),
                true
        );
    }

    private ItemStack createDummyJobItem(final DummyJobCard dummyJobCard) {
        return createItem(
                dummyJobCard.icon(),
                "<gray>" + dummyJobCard.displayName() + "</gray>",
                List.of(
                        "<gray>" + dummyJobCard.description() + "</gray>",
                        "<dark_gray>Demn\u00e4chst verf\u00fcgbar",
                        "<dark_gray>Noch kein spielbarer Pfad hinterlegt."
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
                List.of("<gray>Schlie\u00dft dieses Men\u00fc."),
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
                enabled ? title : "<gray>Ausgegraut</gray>",
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
                .toList();
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(configManager.deserialize(title));
        itemMeta.lore(lines);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glowing) {
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private record DummyJobCard(String displayName, Material icon, String description) {
    }
}

