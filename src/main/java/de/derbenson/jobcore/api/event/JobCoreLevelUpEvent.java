package de.derbenson.jobcore.api.event;

import de.derbenson.jobcore.Job;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class JobCoreLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Job job;
    private final int previousLevel;
    private final int newLevel;

    public JobCoreLevelUpEvent(
            final Player player,
            final Job job,
            final int previousLevel,
            final int newLevel
    ) {
        this.player = player;
        this.job = job;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public Job getJob() {
        return job;
    }

    public int getPreviousLevel() {
        return previousLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
