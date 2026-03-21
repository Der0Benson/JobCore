package de.derbenson.jobcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestMenuManager implements Listener {

    private static final int SIZE = 54;
    private static final int HEADER_SLOT = 4;
    private static final int INSTRUCTION_SLOT = 45;
    private static final int SUMMARY_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private static final Map<QuestPeriod, List<Integer>> CARD_SLOTS = createCardSlots();
    private static final Map<QuestPeriod, Integer> CENTER_SLOTS = Map.of(
            QuestPeriod.DAILY, 19,
            QuestPeriod.WEEKLY, 22,
            QuestPeriod.MONTHLY, 25
    );
    private final ConfigManager configManager;
    private final QuestManager questManager;
    private final ZoneId zoneId;

    public QuestMenuManager(final ConfigManager configManager, final QuestManager questManager) {
        this.configManager = configManager;
        this.questManager = questManager;
        this.zoneId = ZoneId.systemDefault();
    }

    public void openMenu(final Player player, final int ignoredPage) {
        final QuestMenuHolder holder = new QuestMenuHolder(0);
        final Inventory inventory = Bukkit.createInventory(
                holder,
                SIZE,
                configManager.getQuestConfiguration().getString("npc.menu-title", "Missionstafel")
        );
        holder.setInventory(inventory);

        fillBackground(inventory);
        placeHeader(inventory);
        placeQuestCards(player, inventory);
        placeFooter(player, inventory);

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof QuestMenuHolder)) {
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

        final Quest quest = getQuestAtSlot(slot);
        if (quest == null) {
            return;
        }

        final PlayerQuestProgress progress = questManager.getProgress(player.getUniqueId(), quest.id());
        if (progress == null) {
            return;
        }

        if (progress.isClaimed()) {
            player.sendMessage(configManager.getMessage(
                    "messages.quest-cycle-finished",
                    Map.of("quest", quest.displayName(), "period", quest.period().getDisplayName()),
                    "<yellow>Diese %period%-Mission <white>%quest%</white><yellow> ist in diesem Zyklus bereits erledigt.</yellow>"
            ));
            openMenu(player, 0);
            return;
        }

        if (progress.isCompleted()) {
            questManager.claimQuest(player, quest.id());
            openMenu(player, 0);
            return;
        }

        if (progress.isAccepted()) {
            if (event.getClick() == ClickType.RIGHT) {
                questManager.abandonQuest(player, quest.id());
            } else {
                player.sendMessage(configManager.getMessage(
                        "messages.quest-already-active",
                        Map.of("quest", quest.displayName()),
                        "<yellow>Die Mission <white>%quest%</white><yellow> ist bereits aktiv.</yellow>"
                ));
            }
            openMenu(player, 0);
            return;
        }

        questManager.acceptQuest(player, quest.id());
        openMenu(player, 0);
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof QuestMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void fillBackground(final Inventory inventory) {
        final ItemStack light = createPane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        final ItemStack dark = createPane(Material.BLACK_STAINED_GLASS_PANE);
        final ItemStack mid = createPane(Material.GRAY_STAINED_GLASS_PANE);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, light);
        }

        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, dark);
        }

        for (int slot = 36; slot <= 44; slot++) {
            inventory.setItem(slot, mid);
        }

        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, dark);
        }
    }

    private void placeHeader(final Inventory inventory) {
        inventory.setItem(HEADER_SLOT, createItemStack(
                Material.WRITABLE_BOOK,
                configManager.getQuestConfiguration().getString("npc.header-title", "<gold>Missionstafel"),
                configManager.getQuestConfiguration().getStringList("npc.header-lore"),
                false
        ));
    }

    private void placeQuestCards(final Player player, final Inventory inventory) {
        for (final Quest quest : questManager.getQuests()) {
            final PlayerQuestProgress progress = questManager.getProgress(player.getUniqueId(), quest.id());
            final QuestCardState state = resolveState(progress);
            final Material accentMaterial = getAccentMaterial(quest.period(), state);

            for (final int slot : CARD_SLOTS.get(quest.period())) {
                inventory.setItem(slot, createPane(accentMaterial));
            }

            inventory.setItem(CENTER_SLOTS.get(quest.period()), createQuestItem(quest, progress, state));
        }
    }

    private void placeFooter(final Player player, final Inventory inventory) {
        inventory.setItem(INSTRUCTION_SLOT, createItemStack(
                Material.PAPER,
                "<yellow>Steuerung",
                List.of(
                        "<gray>Linksklick auf eine Karte: annehmen oder abgeben",
                        "<gray>Rechtsklick auf eine aktive Karte: abbrechen"
                ),
                false
        ));

        inventory.setItem(SUMMARY_SLOT, createItemStack(
                Material.NETHER_STAR,
                configManager.getQuestConfiguration().getString("npc.header-title", "<gold>Missionstafel"),
                buildSummaryLore(player),
                false
        ));

        inventory.setItem(CLOSE_SLOT, createItemStack(
                Material.BARRIER,
                "<red>Schließen",
                List.of("<gray>Schließt dieses Menü.</gray>"),
                false
        ));
    }

    private List<String> buildSummaryLore(final Player player) {
        final List<String> lore = new ArrayList<>();
        final List<String> configuredLore = configManager.getQuestConfiguration().getStringList("npc.header-lore");
        if (!configuredLore.isEmpty()) {
            lore.addAll(configuredLore);
        }
        lore.add("<gray>Aktiv: <white>" + questManager.getActiveQuestCount(player.getUniqueId()) + "</white>");
        lore.add("<gray>Bereit: <white>" + questManager.getClaimableQuestCount(player.getUniqueId()) + "</white>");
        lore.add("<gray>Erledigt: <white>" + questManager.getClaimedQuestCount(player.getUniqueId()) + "/3</white>");
        return lore;
    }

    private ItemStack createQuestItem(
            final Quest quest,
            final PlayerQuestProgress progress,
            final QuestCardState state
    ) {
        final List<String> lore = new ArrayList<>(quest.description());
        lore.add("<gray>Typ: <white>" + quest.period().getDisplayName() + "</white>");
        lore.add("<gray>Job: <white>" + configManager.getJobDisplayName(quest.job()) + "</white>");
        lore.add("<gray>Fortschritt: <white>" + progress.getProgress() + "/" + quest.requiredAmount() + "</white>");
        lore.add("<gray>Belohnung: <white>+" + quest.rewardXp() + " " + configManager.getJobDisplayName(quest.job()) + "-XP</white>");
        lore.add("<gray>Reset in <white>" + formatResetDuration(quest.period()) + "</white>");
        lore.add(statusLine(state));
        lore.add(actionLine(state));

        return createItemStack(
                getQuestDisplayMaterial(quest, state),
                quest.period().getColorPrefix() + quest.period().getDisplayName() + " <gray>·</gray> <white>" + quest.displayName() + "</white>",
                lore,
                state == QuestCardState.CLAIMABLE || state == QuestCardState.CLAIMED
        );
    }

    private Material getQuestDisplayMaterial(final Quest quest, final QuestCardState state) {
        return switch (state) {
            case CLAIMABLE -> Material.CHEST;
            case CLAIMED -> Material.EMERALD;
            case ACTIVE, AVAILABLE -> quest.icon();
        };
    }

    private Material getAccentMaterial(final QuestPeriod period, final QuestCardState state) {
        return switch (state) {
            case CLAIMABLE -> Material.YELLOW_STAINED_GLASS_PANE;
            case CLAIMED -> Material.LIME_STAINED_GLASS_PANE;
            case ACTIVE, AVAILABLE -> switch (period) {
                case DAILY -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                case WEEKLY -> Material.ORANGE_STAINED_GLASS_PANE;
                case MONTHLY -> Material.PURPLE_STAINED_GLASS_PANE;
            };
        };
    }

    private QuestCardState resolveState(final PlayerQuestProgress progress) {
        if (progress == null) {
            return QuestCardState.AVAILABLE;
        }
        if (progress.isClaimed()) {
            return QuestCardState.CLAIMED;
        }
        if (progress.isCompleted()) {
            return QuestCardState.CLAIMABLE;
        }
        if (progress.isAccepted()) {
            return QuestCardState.ACTIVE;
        }
        return QuestCardState.AVAILABLE;
    }

    private String statusLine(final QuestCardState state) {
        return switch (state) {
            case AVAILABLE -> "<aqua>Verfügbar";
            case ACTIVE -> "<yellow>Aktiv";
            case CLAIMABLE -> "<gold>Abgabebereit";
            case CLAIMED -> "<green>Bereits erledigt";
        };
    }

    private String actionLine(final QuestCardState state) {
        return switch (state) {
            case AVAILABLE -> "<gray>Klicke, um diese Mission zu starten.</gray>";
            case ACTIVE -> "<gray>Rechtsklick, um sie abzubrechen.</gray>";
            case CLAIMABLE -> "<gray>Klicke, um die Belohnung abzuholen.</gray>";
            case CLAIMED -> "<gray>Warte auf den nächsten Zyklus.</gray>";
        };
    }

    private Quest getQuestAtSlot(final int slot) {
        for (final Quest quest : questManager.getQuests()) {
            final List<Integer> slots = CARD_SLOTS.get(quest.period());
            if (slots != null && slots.contains(slot)) {
                return quest;
            }
        }
        return null;
    }

    private String formatResetDuration(final QuestPeriod period) {
        Duration duration = Duration.between(LocalDateTime.now(zoneId), period.nextReset(zoneId));
        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }

        final long totalMinutes = Math.max(0L, duration.toMinutes());
        final long days = totalMinutes / (60L * 24L);
        final long hours = (totalMinutes % (60L * 24L)) / 60L;
        final long minutes = totalMinutes % 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private ItemStack createPane(final Material material) {
        return createItemStack(material, "<gray> ", List.of(), false);
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

    private static Map<QuestPeriod, List<Integer>> createCardSlots() {
        final Map<QuestPeriod, List<Integer>> slots = new LinkedHashMap<>();
        slots.put(QuestPeriod.DAILY, List.of(9, 10, 11, 18, 19, 20, 27, 28, 29));
        slots.put(QuestPeriod.WEEKLY, List.of(12, 13, 14, 21, 22, 23, 30, 31, 32));
        slots.put(QuestPeriod.MONTHLY, List.of(15, 16, 17, 24, 25, 26, 33, 34, 35));
        return slots;
    }

    private Component withoutItalic(final Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private enum QuestCardState {
        AVAILABLE,
        ACTIVE,
        CLAIMABLE,
        CLAIMED
    }
}

