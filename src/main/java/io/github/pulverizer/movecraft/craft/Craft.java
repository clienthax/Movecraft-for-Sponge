package io.github.pulverizer.movecraft.craft;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.async.detection.DetectionTask;
import io.github.pulverizer.movecraft.async.rotation.RotationTask;
import io.github.pulverizer.movecraft.async.translation.TranslationTask;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.events.CraftSinkEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.spongepowered.api.text.chat.ChatTypes.ACTION_BAR;

public class Craft {

    //Facts
    private final CraftType type;
    private final UUID id = UUID.randomUUID();
    private final HashHitBox initialHitbox;
    private final Long originalPilotTime;


    //State
    private AtomicBoolean processing = new AtomicBoolean();
    private HashHitBox currentHitbox;
    private CraftState state;
    private Long lastCheckTime;
    private World world;


    //Crew
    private Player pilot;
    private Player AADirector;
    private Player cannonDirector;
    private Set<Player> crew = new HashSet<>();


    //Direct Control
    private boolean directControl = false;
    private Vector3d pilotPosition;


    //Movement
    private long lastCruiseUpdateTime;
    private Direction cruiseDirection;
    private Vector3i lastMoveVector;
    private Set<BlockSnapshot> phasedBlocks = new HashSet<>();
    private double burningFuel;
    private int fuelStored;
    private int numberOfMoves = 0;
    private long lastRotateTime = 0;
    private float meanMoveTime;

    //TODO: Rewrite these variables
    private final HashMap<UUID, Location<World>> crewSigns = new HashMap<>();

    public Craft(CraftType type, World world) {
        this.type = type;
        setWorld(world);
        setLastCruiseUpdateTime(System.currentTimeMillis() - 10000);
        this.originalPilotTime = System.currentTimeMillis();
    }

    public boolean isNotProcessing() {
        return !processing.get();
    }

    public void setProcessing(boolean processing) {
        this.processing.set(processing);
    }

    public HashHitBox getHitBox() {
        return currentHitbox;
    }

    public void setHitBox(HashHitBox newHitBox){
        currentHitbox = newHitBox;
    }

    public CraftType getType() {
        return type;
    }

    private void setWorld(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }


    public void detect(Player player, Location<World> startPoint) {
        setPilot(player);
        Movecraft.getInstance().getAsyncManager().submitTask(new DetectionTask(this, startPoint, player), this);
    }

    public void translate(int dx, int dy, int dz) {
        //TODO: Rewrite to Vectors instead of int.

        // check to see if the craft is trying to move in a direction not permitted by the type
        if (!this.getType().allowHorizontalMovement() && this.getState() != CraftState.SINKING) {
            dx = 0;
            dz = 0;
        }
        if (!this.getType().allowVerticalMovement() && this.getState() != CraftState.SINKING) {
            dy = 0;
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }

        if (!this.getType().allowVerticalTakeoffAndLanding() && dy != 0 && this.getState() != CraftState.SINKING) {
            if (dx == 0 && dz == 0) {
                return;
            }
        }

        Movecraft.getInstance().getAsyncManager().submitTask(new TranslationTask(this, dx, dy, dz), this);
    }

    public void rotate(Rotation rotation, MovecraftLocation originPoint) {
        if(getLastRotateTime()+1e9>System.nanoTime()){
            if(getPilot()!=null)
                getPilot().sendMessage(Text.of("You're turning too quickly!"));
            return;
        }
        setLastRotateTime(System.nanoTime());
        Movecraft.getInstance().getAsyncManager().submitTask(new RotationTask(this, originPoint, rotation, this.getWorld()), this);
    }

    public void rotate(Rotation rotation, MovecraftLocation originPoint, boolean isSubCraft) {
        Movecraft.getInstance().getAsyncManager().submitTask(new RotationTask(this, originPoint, rotation, this.getWorld(), isSubCraft), this);
    }

    public CraftState getState() {
        return this.state;
    }

    public void setCruising(boolean cruising) {
        if(pilot != null){
            pilot.sendMessage(ACTION_BAR, Text.of("Cruising " + (cruising ? "enabled" : "disabled")));
        }
        if (cruising)
            state = CraftState.CRUISING;
        else if (getState() != CraftState.DISABLED && getState() != CraftState.SINKING){
            state = CraftState.STOPPED;
        }
    }

    public void sink(){
        CraftSinkEvent event = new CraftSinkEvent(this);
        Sponge.getEventManager().post(event);
        if(event.isCancelled()){
            return;
        }
        this.state = CraftState.SINKING;

    }

    public void setDisabled() {
        this.state = CraftState.DISABLED;
    }

    public Direction getCruiseDirection() {
        return cruiseDirection;
    }

    public void setCruiseDirection(Direction cruiseDirection) {
        this.cruiseDirection = cruiseDirection;
    }

    public void setLastCruiseUpdateTime(long update) {
        this.lastCruiseUpdateTime = update;
    }

    public long getLastCruiseUpdateTime() {
        return lastCruiseUpdateTime;
    }

    public long getLastCheckTime() {
        return lastCheckTime;
    }

    public void setLastCheckTime(long update) {
        this.lastCheckTime = update;
    }

    public void setLastMoveVector(Vector3i lastMoveVector) {
        this.lastMoveVector = lastMoveVector;
    }

    public Vector3i getLastMoveVector() {
        return lastMoveVector;
    }

    public boolean getDirectControl() {
        return directControl;
    }

    public void setDirectControl(boolean directControl) {
        this.directControl = directControl;
    }

    public Vector3d getPilotPosition() {
        return pilotPosition;
    }

    public void setPilotPosition(Vector3d pilotPosition) {
        this.pilotPosition = pilotPosition;
    }

    public double getBurningFuel() {
        return burningFuel;
    }

    public void setBurningFuel(double burningFuel) {
        this.burningFuel = burningFuel;
    }

    public int getInitialSize() {
        return initialHitbox.size();
    }

    public Player getPilot() {
        return pilot;
    }

    public void setPilot(Player player) {
        pilot = player;
    }

    public Player getCannonDirector() {
        return cannonDirector;
    }

    public void setCannonDirector(Player player) {
        cannonDirector = player;
    }

    public Player getAADirector() {
        return AADirector;
    }

    public void setAADirector(Player player) {
        AADirector = player;
    }

    public long getOriginalPilotTime() {
        return originalPilotTime;
    }

    public float getMeanMoveTime() {
        return meanMoveTime;
    }

    public void addMoveTime(float moveTime){
        meanMoveTime = (meanMoveTime* numberOfMoves + moveTime)/(++numberOfMoves);
    }

    public int getTickCooldown() {
        if(state == CraftState.SINKING)
            return type.getSinkRateTicks();
        double chestPenalty = 0;
        for(MovecraftLocation location : currentHitbox){
            if(location.toSponge(world).getBlockType()==BlockTypes.CHEST)
                chestPenalty++;
        }
        chestPenalty*=type.getChestPenalty();
        if(meanMoveTime==0)
            return type.getCruiseTickCooldown()+(int)chestPenalty;
        if(state != CraftState.CRUISING)
            return type.getTickCooldown()+(int)chestPenalty;
        if(type.getDynamicFlyBlockSpeedFactor() != 0){
            double count = 0;
            BlockType flyBlockMaterial = type.getDynamicFlyBlock();
            for(MovecraftLocation location : currentHitbox){
                if(location.toSponge(world).getBlockType()==flyBlockMaterial)
                    count++;
            }
            return Math.max((int) (20 / (type.getCruiseTickCooldown() * (1  + type.getDynamicFlyBlockSpeedFactor() * (count / currentHitbox.size() - .5)))), 1);
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
        if (type.getStaticWaterLevel() != 0 || currentHitbox.isEmpty()) {
            return type.getStaticWaterLevel();
        }

        // figure out the water level by examining blocks next to the outer boundaries of the craft
        for (int posY = currentHitbox.getMaxY() + 1; posY >= currentHitbox.getMinY() - 1; posY--) {
            int numWater = 0;
            int numAir = 0;
            int posX;
            int posZ;
            posZ = currentHitbox.getMinZ() - 1;
            for (posX = currentHitbox.getMinX() - 1; posX <= currentHitbox.getMaxX() + 1; posX++) {
                BlockType typeID = world.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posZ = currentHitbox.getMaxZ() + 1;
            for (posX = currentHitbox.getMinX() - 1; posX <= currentHitbox.getMaxX() + 1; posX++) {
                BlockType typeID = world.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posX = currentHitbox.getMinX() - 1;
            for (posZ = currentHitbox.getMinZ(); posZ <= currentHitbox.getMaxZ(); posZ++) {
                BlockType typeID = world.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posX = currentHitbox.getMaxX() + 1;
            for (posZ = currentHitbox.getMinZ(); posZ <= currentHitbox.getMaxZ(); posZ++) {
                BlockType typeID = world.getBlock(posX, posY, posZ).getType();
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

    public Set<BlockSnapshot> getPhasedBlocks(){
        return this.phasedBlocks;
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