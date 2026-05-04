package de.derbenson.jobcore.api.event;

import de.derbenson.jobcore.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class JobCoreQuestClaimEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Quest quest;
    private int rewardXp;
    private boolean cancelled;

    public JobCoreQuestClaimEvent(final Player player, final Quest quest, final int rewardXp) {
        this.player = player;
        this.quest = quest;
        this.rewardXp = Math.max(0, rewardXp);
    }

    public Player getPlayer() {
        return player;
    }

    public Quest getQuest() {
        return quest;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public void setRewardXp(final int rewardXp) {
        this.rewardXp = Math.max(0, rewardXp);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
