package io.github.pulverizer.movecraft.craft;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.async.detection.DetectionTask;
import io.github.pulverizer.movecraft.async.rotation.RotationTask;
import io.github.pulverizer.movecraft.async.translation.TranslationTask;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.events.CraftSinkEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;

import io.github.pulverizer.movecraft.Movecraft;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.spongepowered.api.text.chat.ChatTypes.ACTION_BAR;

public class Craft {

    protected final CraftType type;
    protected HashHitBox hitBox;

    protected World w;
    private final AtomicBoolean processing = new AtomicBoolean();
    private int maxHeightLimit;
    private boolean cruising;
    private boolean sinking;
    private boolean disabled;
    private Direction cruiseDirection;
    private long lastCruiseUpdate;
    private long lastBlockCheck;
    private long lastRotateTime=0;
    private long origPilotTime;
    private int lastDX, lastDY, lastDZ;
    private double burningFuel;
    private boolean pilotLocked;
    private double pilotLockedX;
    private double pilotLockedY;
    private int origBlockCount;
    private double pilotLockedZ;
    private Player notificationPlayer;
    private Player cannonDirector;
    private Player AADirector;
    private float meanMoveTime;
    private int numMoves;
    private final Map<MovecraftLocation, BlockSnapshot> phaseBlocks = new HashMap<>();
    private final HashMap<UUID, Location<World>> crewSigns = new HashMap<>();
    private final UUID id = UUID.randomUUID();

    public Craft(CraftType type, World world) {
        this.type = type;
        this.w = world;
        this.hitBox = new HashHitBox();
        if (type.getMaxHeightLimit() > w.getDimension().getBuildHeight() - 1) {
            this.maxHeightLimit = w.getDimension().getBuildHeight() - 1;
        } else {
            this.maxHeightLimit = type.getMaxHeightLimit();
        }
        this.pilotLocked = false;
        this.pilotLockedX = 0.0;
        this.pilotLockedY = 0.0;
        this.pilotLockedZ = 0.0;
        this.cannonDirector = null;
        this.AADirector = null;
        this.lastCruiseUpdate = System.currentTimeMillis() - 10000;
        this.cruising = false;
        this.sinking = false;
        this.disabled = false;
        this.origPilotTime = System.currentTimeMillis();
        numMoves = 0;
    }

    public boolean isNotProcessing() {
        return !processing.get();
    }

    public void setProcessing(boolean processing) {
        this.processing.set(processing);
    }

    public HashHitBox getHitBox() {
        return hitBox;
    }

    public void setHitBox(HashHitBox hitBox){
        this.hitBox = hitBox;
    }

    public CraftType getType() {
        return type;
    }

    public World getW() {
        return w;
    }


    public void detect(Player player, Player notificationPlayer, MovecraftLocation startPoint) {
        this.setNotificationPlayer(notificationPlayer);
        Movecraft.getInstance().getAsyncManager().submitTask(new DetectionTask(this, startPoint, player), this);
    }

    public void translate(int dx, int dy, int dz) {
        //TODO: Rewrite to Vectors instead of int.
        // check to see if the craft is trying to move in a direction not permitted by the type
        if (!this.getType().allowHorizontalMovement() && !this.getSinking()) {
            dx = 0;
            dz = 0;
        }
        if (!this.getType().allowVerticalMovement() && !this.getSinking()) {
            dy = 0;
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }

        if (!this.getType().allowVerticalTakeoffAndLanding() && dy != 0 && !this.getSinking()) {
            if (dx == 0 && dz == 0) {
                return;
            }
        }

        Movecraft.getInstance().getAsyncManager().submitTask(new TranslationTask(this, dx, dy, dz), this);
    }

    public void rotate(Rotation rotation, MovecraftLocation originPoint) {
        if(getLastRotateTime()+1e9>System.nanoTime()){
            if(getNotificationPlayer()!=null)
                getNotificationPlayer().sendMessage(Text.of("You're turning too quickly!"));
            return;
        }
        setLastRotateTime(System.nanoTime());
        Movecraft.getInstance().getAsyncManager().submitTask(new RotationTask(this, originPoint, rotation, this.getW()), this);
    }

    public void rotate(Rotation rotation, MovecraftLocation originPoint, boolean isSubCraft) {
        Movecraft.getInstance().getAsyncManager().submitTask(new RotationTask(this, originPoint, rotation, this.getW(), isSubCraft), this);
    }

    public boolean getCruising() {
        return cruising;
    }

    public void setCruising(boolean cruising) {
        if(notificationPlayer!=null){
            notificationPlayer.sendMessage(ACTION_BAR, Text.of("Cruising " + (cruising ? "enabled" : "disabled")));
        }
        this.cruising = cruising;
    }

    public boolean getSinking() {
        return sinking;
    }

    public void sink(){
        CraftSinkEvent event = new CraftSinkEvent(this);
        Sponge.getEventManager().post(event);
        if(event.isCancelled()){
            return;
        }
        this.sinking = true;

    }

    public boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public Direction getCruiseDirection() {
        return cruiseDirection;
    }

    public void setCruiseDirection(Direction cruiseDirection) {
        this.cruiseDirection = cruiseDirection;
    }

    public void setLastCruiseUpdate(long update) {
        this.lastCruiseUpdate = update;
    }

    public long getLastCruiseUpdate() {
        return lastCruiseUpdate;
    }

    public long getLastBlockCheck() {
        return lastBlockCheck;
    }

    public void setLastBlockCheck(long update) {
        this.lastBlockCheck = update;
    }

    public int getLastDX() {
        return lastDX;
    }

    public void setLastDX(int dX) {
        this.lastDX = dX;
    }

    public int getLastDY() {
        return lastDY;
    }

    public void setLastDY(int dY) {
        this.lastDY = dY;
    }

    public int getLastDZ() {
        return lastDZ;
    }

    public void setLastDZ(int dZ) {
        this.lastDZ = dZ;
    }

    public boolean getPilotLocked() {
        return pilotLocked;
    }

    public void setPilotLocked(boolean pilotLocked) {
        this.pilotLocked = pilotLocked;
    }

    public double getPilotLockedX() {
        return pilotLockedX;
    }

    public void setPilotLockedX(double pilotLockedX) {
        this.pilotLockedX = pilotLockedX;
    }

    public double getPilotLockedY() {
        return pilotLockedY;
    }

    public void setPilotLockedY(double pilotLockedY) {
        this.pilotLockedY = pilotLockedY;
    }

    public double getPilotLockedZ() {
        return pilotLockedZ;
    }

    public void setPilotLockedZ(double pilotLockedZ) {
        this.pilotLockedZ = pilotLockedZ;
    }

    public double getBurningFuel() {
        return burningFuel;
    }

    public void setBurningFuel(double burningFuel) {
        this.burningFuel = burningFuel;
    }

    public int getOrigBlockCount() {
        return origBlockCount;
    }

    public void setOrigBlockCount(int origBlockCount) {
        this.origBlockCount = origBlockCount;
    }

    public Player getNotificationPlayer() {
        return notificationPlayer;
    }

    public void setNotificationPlayer(Player notificationPlayer) {
        this.notificationPlayer = notificationPlayer;
    }

    public Player getCannonDirector() {
        return cannonDirector;
    }

    public void setCannonDirector(Player cannonDirector) {
        this.cannonDirector = cannonDirector;
    }

    public Player getAADirector() {
        return AADirector;
    }

    public void setAADirector(Player AADirector) {
        this.AADirector = AADirector;
    }

    public long getOrigPilotTime() {
        return origPilotTime;
    }

    public float getMeanMoveTime() {
        return meanMoveTime;
    }

    public void addMoveTime(float moveTime){
        meanMoveTime = (meanMoveTime*numMoves + moveTime)/(++numMoves);
    }

    public int getTickCooldown() {
        if(sinking)
            return type.getSinkRateTicks();
        double chestPenalty = 0;
        for(MovecraftLocation location : hitBox){
            if(location.toSponge(w).getBlockType()==BlockTypes.CHEST)
                chestPenalty++;
        }
        chestPenalty*=type.getChestPenalty();
        if(meanMoveTime==0)
            return type.getCruiseTickCooldown()+(int)chestPenalty;
        if(!cruising)
            return type.getTickCooldown()+(int)chestPenalty;
        if(type.getDynamicFlyBlockSpeedFactor()!=0){
            double count = 0;
            BlockType flyBlockMaterial = type.getDynamicFlyBlock();
            for(MovecraftLocation location : hitBox){
                if(location.toSponge(w).getBlockType()==flyBlockMaterial)
                    count++;
            }
            return Math.max((int) (20 / (type.getCruiseTickCooldown() * (1  + type.getDynamicFlyBlockSpeedFactor() * (count /hitBox.size() - .5)))), 1);
            //return  Math.max((int)(type.getCruiseTickCooldown()* (1 - count /hitBox.size()) +chestPenalty),1);
        }

        if(type.getDynamicLagSpeedFactor()==0)
            return type.getCruiseTickCooldown()+(int)chestPenalty;
        //TODO: modify skip blocks by an equal proportion to this, than add another modifier based on dynamic speed factor
        return Math.max((int)(type.getCruiseTickCooldown()*meanMoveTime*20/type.getDynamicLagSpeedFactor() +chestPenalty),1);
    }

    /**
     * gets the speed of a craft in blocks per second.
     * @return the speed of the craft
     */
    public double getSpeed(){
        return 20*type.getCruiseSkipBlocks()/(double)getTickCooldown();
    }

    public long getLastRotateTime() {
        return lastRotateTime;
    }

    public void setLastRotateTime(long lastRotateTime) {
        this.lastRotateTime = lastRotateTime;
    }

    public int getWaterLine(){
        //TODO: Remove this temporary system in favor of passthrough blocks
        // Find the waterline from the surrounding terrain or from the static level in the craft type
        int waterLine = 0;
        if (type.getStaticWaterLevel() != 0 || hitBox.isEmpty()) {
            return type.getStaticWaterLevel();
        }

        // figure out the water level by examining blocks next to the outer boundaries of the craft
        for (int posY = hitBox.getMaxY() + 1; posY >= hitBox.getMinY() - 1; posY--) {
            int numWater = 0;
            int numAir = 0;
            int posX;
            int posZ;
            posZ = hitBox.getMinZ() - 1;
            for (posX = hitBox.getMinX() - 1; posX <= hitBox.getMaxX() + 1; posX++) {
                BlockType typeID = w.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posZ = hitBox.getMaxZ() + 1;
            for (posX = hitBox.getMinX() - 1; posX <= hitBox.getMaxX() + 1; posX++) {
                BlockType typeID = w.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posX = hitBox.getMinX() - 1;
            for (posZ = hitBox.getMinZ(); posZ <= hitBox.getMaxZ(); posZ++) {
                BlockType typeID = w.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posX = hitBox.getMaxX() + 1;
            for (posZ = hitBox.getMinZ(); posZ <= hitBox.getMaxZ(); posZ++) {
                BlockType typeID = w.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            if (numWater > numAir) {
                return posY;
            }
        }
        return waterLine;
    }

    public Map<MovecraftLocation,BlockSnapshot> getPhaseBlocks(){
        return phaseBlocks;
    }

    public Map<UUID, Location<World>> getCrewSigns(){
        return crewSigns;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Craft)){
            return false;
        }
        return this.id.equals(((Craft)obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}