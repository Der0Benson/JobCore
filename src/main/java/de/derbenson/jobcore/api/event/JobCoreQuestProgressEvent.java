package de.derbenson.jobcore.api.event;

import de.derbenson.jobcore.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class JobCoreQuestProgressEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Quest quest;
    private final String target;
    private final int previousProgress;
    private int newProgress;
    private boolean cancelled;

    public JobCoreQuestProgressEvent(
            final Player player,
            final Quest quest,
            final String target,
            final int previousProgress,
            final int newProgress
    ) {
        this.player = player;
        this.quest = quest;
        this.target = target;
        this.previousProgress = Math.max(0, previousProgress);
        this.newProgress = Math.max(0, newProgress);
    }

    public Player getPlayer() {
        return player;
    }

    public Quest getQuest() {
        return quest;
    }

    public String getTarget() {
        return target;
    }

    public int getPreviousProgress() {
        return previousProgress;
    }

    public int getNewProgress() {
        return newProgress;
    }

    public void setNewProgress(final int newProgress) {
        this.newProgress = Math.max(0, newProgress);
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
