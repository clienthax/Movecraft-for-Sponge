package io.github.pulverizer.movecraft.async.detection;

import io.github.pulverizer.movecraft.utils.HashHitBox;
import io.github.pulverizer.movecraft.utils.HitBox;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.World;

@Deprecated
public class DetectionTaskData {
    public Double dynamicFlyBlockSpeedMultiplier;
    private World w;
    private boolean failed;
    private boolean waterContact;
    private String failMessage;
    private HashHitBox hitBox;
    private Player player;
    private Player notificationPlayer;
    private int minX, minZ;
    private BlockType[] allowedBlocks, forbiddenBlocks;
    private String[] forbiddenSignStrings;

    public DetectionTaskData(World w, Player player, Player notificationPlayer, BlockType[] allowedBlocks, BlockType[] forbiddenBlocks, String[] forbiddenSignStrings) {
        this.w = w;
        this.player = player;
        this.notificationPlayer = notificationPlayer;
        this.allowedBlocks = allowedBlocks;
        this.forbiddenBlocks = forbiddenBlocks;
        this.forbiddenSignStrings = forbiddenSignStrings;
        this.waterContact = false;
    }

    public DetectionTaskData() {
    }

    public BlockType[] getAllowedBlocks() {
        return allowedBlocks;
    }

    public BlockType[] getForbiddenBlocks() {
        return forbiddenBlocks;
    }

    public String[] getForbiddenSignStrings() {
        return forbiddenSignStrings;
    }

    public World getWorld() {
        return w;
    }

    void setWorld(World w) {
        this.w = w;
    }

    public boolean failed() {
        return failed;
    }

    public boolean getWaterContact() {
        return waterContact;
    }

    void setWaterContact(boolean waterContact) {
        this.waterContact = waterContact;
    }

    public String getFailMessage() {
        return failMessage;
    }

    void setFailMessage(String failMessage) {
        this.failMessage = failMessage;
    }

    @Deprecated
    public HashHitBox getBlockList() {
        return hitBox;
    }

    @Deprecated
    void setBlockList(HashHitBox blockList) {
        this.hitBox = blockList;
    }

    public Player getPlayer() {
        return player;
    }

    void setPlayer(Player player) {
        this.player = player;
    }

    public Player getNotificationPlayer() {
        return notificationPlayer;
    }

    void setNotificationPlayer(Player notificationPlayer) {
        this.notificationPlayer = notificationPlayer;
    }

    public HitBox getHitBox() {
        return hitBox;
    }

    void setHitBox(HashHitBox hitBox) {
        this.hitBox = hitBox;
    }

    public Integer getMinX() {
        return minX;
    }

    void setMinX(Integer minX) {
        this.minX = minX;
    }

    public Integer getMinZ() {
        return minZ;
    }

    void setMinZ(Integer minZ) {
        this.minZ = minZ;
    }

    void setFailed(boolean failed) {
        this.failed = failed;
    }
}