package io.github.pulverizer.movecraft.craft.crew;

import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import org.spongepowered.api.Sponge;

import java.util.UUID;

public class CrewInvite {
    private UUID invitedPlayer;
    private String invitedBy;
    private UUID craftUUID;
    private String message;
    private int tickSent;

    public CrewInvite(UUID invitedPlayer, String invitedBy, Craft craft) {
        this.invitedPlayer = invitedPlayer;
        this.invitedBy = invitedBy;
        craftUUID = craft.getId();

        tickSent = Sponge.getServer().getRunningTimeTicks();
        message = String.format("You have been invited by %s to serve under them aboard their %s.", invitedBy, craft.getType().getName());
    }

    public CrewInvite(UUID invitedPlayer, String invitedBy, Craft craft, String craftName) {
        this.invitedPlayer = invitedPlayer;
        this.invitedBy = invitedBy;
        craftUUID = craft.getId();

        tickSent = Sponge.getServer().getRunningTimeTicks();
        message = String.format("You have been invited by %s to serve under them aboard their %s, the %s.", invitedBy, craft.getType().getName(), craftName);
    }

    public UUID getInvitedPlayerUUID() {
        return invitedPlayer;
    }

    public String getInvitedBy() {
        return invitedBy;
    }

    public Craft getCraft() {
        return CraftManager.getInstance().getCraftByUUID(craftUUID);
    }

    public int getTicksSinceSent() {
        return Sponge.getServer().getRunningTimeTicks() - tickSent;
    }

    public String getMessage() {
        return message;
    }
}
