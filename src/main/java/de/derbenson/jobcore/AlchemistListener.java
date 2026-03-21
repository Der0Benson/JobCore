package de.derbenson.jobcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AlchemistListener implements Listener {

    private final JobManager jobManager;
    private final ConfigManager configManager;
    private final DebugManager debugManager;
    private final QuestManager questManager;
    private final Map<StandKey, BrewOwner> standOwners = new HashMap<>();

    public AlchemistListener(
            final JobManager jobManager,
            final ConfigManager configManager,
            final DebugManager debugManager,
            final QuestManager questManager
    ) {
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.debugManager = debugManager;
        this.questManager = questManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        if (event.getClickedBlock().getType() != Material.BREWING_STAND) {
            return;
        }

        markOwner(event.getClickedBlock(), event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory() instanceof BrewerInventory brewerInventory)) {
            return;
        }

        final BrewingStand brewingStand = brewerInventory.getHolder();
        if (brewingStand == null) {
            return;
        }

        markOwner(brewingStand.getBlock(), player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory() instanceof BrewerInventory brewerInventory)) {
            return;
        }

        final BrewingStand brewingStand = brewerInventory.getHolder();
        if (brewingStand == null) {
            return;
        }

        markOwner(brewingStand.getBlock(), player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent event) {
        if (!configManager.getJobConfiguration(Job.ALCHEMIST).getBoolean("anti-farm.invalidate-on-hopper", true)) {
            return;
        }

        final Block sourceStand = resolveBrewingStand(event.getSource());
        final Block destinationStand = resolveBrewingStand(event.getDestination());
        final Block stand = sourceStand != null ? sourceStand : destinationStand;
        if (stand == null) {
            return;
        }

        standOwners.remove(StandKey.of(stand));
        debugManager.sendXpDebug("Alchemisten-XP an Braustand " + stand.getX() + "/" + stand.getY() + "/" + stand.getZ()
                + " deaktiviert, weil Hopper oder Automatisierung erkannt wurden.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(final BrewEvent event) {
        final Block block = event.getBlock();
        final StandKey standKey = StandKey.of(block);
        final BrewOwner owner = standOwners.get(standKey);
        if (owner == null) {
            debugManager.sendXpDebug("Keine Alchemisten-XP an Braustand " + block.getX() + "/" + block.getY() + "/" + block.getZ()
                    + ", weil keine frische Spielerinteraktion vorliegt.");
            return;
        }

        final long ownerTimeoutMillis = Math.max(1L, configManager.getJobConfiguration(Job.ALCHEMIST).getLong("anti-farm.interaction-seconds", 45L)) * 1000L;
        if ((System.currentTimeMillis() - owner.timestamp()) > ownerTimeoutMillis) {
            standOwners.remove(standKey);
            debugManager.sendXpDebug("Keine Alchemisten-XP an Braustand " + block.getX() + "/" + block.getY() + "/" + block.getZ()
                    + ", weil die letzte Spielerinteraktion abgelaufen ist.");
            return;
        }

        final Player player = Bukkit.getPlayer(owner.playerUuid());
        if (player == null || !player.isOnline()) {
            debugManager.sendXpDebug("Keine Alchemisten-XP an Braustand " + block.getX() + "/" + block.getY() + "/" + block.getZ()
                    + ", weil der Besitzer nicht online ist.");
            return;
        }

        final double maxDistance = Math.max(0.0D, configManager.getJobConfiguration(Job.ALCHEMIST).getDouble("anti-farm.max-player-distance", 8.0D));
        if (!player.getWorld().equals(block.getWorld()) || player.getLocation().distanceSquared(block.getLocation().add(0.5D, 0.5D, 0.5D)) > (maxDistance * maxDistance)) {
            debugManager.sendXpDebug("Keine Alchemisten-XP für " + player.getName() + ", weil er zu weit vom Braustand entfernt ist.");
            return;
        }

        final BrewerInventory inventory = event.getContents();
        final ItemStack ingredient = inventory.getIngredient();
        if (ingredient == null || ingredient.getType().isAir()) {
            return;
        }

        final Job job = Job.ALCHEMIST;
        final int baseXp = jobManager.getConfiguredXpValue(job, ingredient.getType());
        if (baseXp <= 0) {
            return;
        }

        final int brewedPotionCount = countPotionOutputs(inventory);
        if (brewedPotionCount <= 0) {
            return;
        }

        final boolean doubleDrops = jobManager.shouldDoubleDrops(player.getUniqueId(), job);
        final Optional<BonusDropEntry> bonusDrop = jobManager.rollBonusDrop(player.getUniqueId(), job);
        final int granted = jobManager.grantExperience(player, job, baseXp * brewedPotionCount);

        if (granted > 0) {
            questManager.recordObjective(player, job, QuestObjectiveType.BREW_INGREDIENT, ingredient.getType().name(), brewedPotionCount);
            debugManager.sendXpDebug(player.getName() + " erhielt " + granted + " Alchemisten-XP für "
                    + ingredient.getType().name() + " an Braustand " + block.getX() + "/" + block.getY() + "/" + block.getZ() + ".");
        }

        if (doubleDrops) {
            for (int slot = 0; slot < 3; slot++) {
                final ItemStack potion = inventory.getItem(slot);
                if (isPotionItem(potion)) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), potion.clone());
                }
            }
        }

        bonusDrop.ifPresent(entry -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), entry.toItemStack()));
    }

    private void markOwner(final Block block, final Player player) {
        standOwners.put(StandKey.of(block), new BrewOwner(player.getUniqueId(), System.currentTimeMillis()));
    }

    private int countPotionOutputs(final BrewerInventory inventory) {
        int count = 0;
        for (int slot = 0; slot < 3; slot++) {
            if (isPotionItem(inventory.getItem(slot))) {
                count++;
            }
        }
        return count;
    }

    private boolean isPotionItem(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }

        return itemStack.getType() == Material.POTION
                || itemStack.getType() == Material.SPLASH_POTION
                || itemStack.getType() == Material.LINGERING_POTION;
    }

    private Block resolveBrewingStand(final Inventory inventory) {
        if (!(inventory instanceof BrewerInventory brewerInventory)) {
            return null;
        }

        final BrewingStand brewingStand = brewerInventory.getHolder();
        return brewingStand == null ? null : brewingStand.getBlock();
    }

    private record BrewOwner(UUID playerUuid, long timestamp) {
    }

    private record StandKey(UUID worldUuid, int x, int y, int z) {

        private static StandKey of(final Block block) {
            return new StandKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}

