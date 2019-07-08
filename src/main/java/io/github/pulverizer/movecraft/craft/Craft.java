package io.github.pulverizer.movecraft.craft;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.Rotation;
import io.github.pulverizer.movecraft.async.detection.DetectionTask;
import io.github.pulverizer.movecraft.async.rotation.RotationTask;
import io.github.pulverizer.movecraft.async.translation.TranslationTask;
import io.github.pulverizer.movecraft.events.CraftSinkEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.carrier.Furnace;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Craft {

    //Facts
    private final CraftType type;
    private final UUID id = UUID.randomUUID();
    private int initialSize;
    private final Long originalPilotTime;
    private UUID originalPilot;


    //State
    private AtomicBoolean processing = new AtomicBoolean();
    private HashHitBox currentHitbox;
    private CraftState state;
    private Long lastCheckTime;
    private World world;


    //Crew
    private UUID pilot;
    private UUID AADirector;
    private UUID cannonDirector;
    private HashSet<UUID> crewList = new HashSet<>();


    //Direct Control
    private boolean directControl = false;
    private Vector3d pilotOffset = new Vector3d(0,0,0);


    //Movement
    private long lastCruiseUpdateTime;
    private Direction cruiseDirection;
    private Vector3i lastMoveVector;
    private HashSet<BlockSnapshot> phasedBlocks = new HashSet<>();
    private double burningFuel;
    private int numberOfMoves = 0;
    private long lastRotateTime = 0;
    private float meanMoveTime;

    //TODO: Rewrite these variables
    private final HashMap<UUID, Location<World>> crewSigns = new HashMap<>();

    /**
     * Initialises the craft and detects the craft's hitbox.
     * @param type The type of craft to detect.
     * @param player The player that triggered craft detection.
     * @param startLocation Location from which to start craft detection.
     */
    public Craft(CraftType type, UUID player, Location<World> startLocation) {
        this.type = type;
        setWorld(startLocation.getExtent());
        addCrewMember(player);
        setLastCruiseUpdateTime(System.currentTimeMillis() - 10000);
        this.originalPilotTime = System.currentTimeMillis();
        this.originalPilot = player;
        Movecraft.getInstance().getAsyncManager().submitTask(new DetectionTask(this, startLocation, player), this);
    }

    /**
     * Fetches the type of craft this is.
     * @return The type of craft this is.
     */
    public CraftType getType() {
        return type;
    }

    /**
     * Adds the player to the craft's crew.
     * @param player The player to be added.
     */
    public void addCrewMember(UUID player) {
        crewList.add(player);
    }

    /**
     * Removes the player from the craft's crew.
     * @param player The player to be removed.
     */
    public void removeCrewMember(UUID player) {
        crewList.remove(player);

        if (getPilot() == player)
            setPilot(null);

        if (getAADirector() == player)
            setAADirector(null);

        if (getCannonDirector() == player)
            setCannonDirector(null);
    }

    /**
     * Fetches the list of players that are registered as members of the craft's crew.
     * @return The list of players that are registered as members of the craft's crew.
     */
    public HashSet<UUID> getCrewList() {
        return crewList;
    }

    /**
     * Checks if the player is a member of the craft's crew.
     * @param player Player to look for.
     * @return True if the player is a part of the crew.
     */
    public boolean isCrewMember(UUID player) {
        return crewList.contains(player);
    }

    /**
     * <pre>
     *     Clears the crew list of any players it contains.
     *     Unless the ship is sinking it will be released next time checks are performed.
     *     Players will be unable to pilot the craft until it is released.
     * </pre>
     */
    public void removeCrew() {
        crewList.clear();
        setPilot(null);
        setAADirector(null);
        setCannonDirector(null);
    }

    /**
     * Sets and updates the initial size of the craft.
     * @param size The initial size of the craft.
     */
    public void setInitialSize(int size) {
        initialSize = size;
    }

    /**
     * Fetches the initial size of the craft.
     * @return The initial size of the craft.
     */
    public int getInitialSize() {
        return initialSize;
    }

    /**
     * Sets the current hitbox of the craft.
     * @param newHitbox The craft's new hitbox.
     */
    public void setHitBox(HashHitBox newHitbox) {
        currentHitbox = newHitbox;
    }

    /**
     * Fetches the craft's current hitbox.
     * @return The craft's current hitbox.
     */
    public HashHitBox getHitBox() {
        return currentHitbox;
    }

    /**
     * Fetches the craft's current size.
     * @return The craft's current size.
     */
    public int getSize() {
        return currentHitbox.size();
    }

    //Cross-dimensional crafts anyone?
    /**
     * <pre>
     *     Changes the world the craft is registered as being in.
     *     Do NOT call this method until the craft has been translated.
     * </pre>
     * @param world The world the craft has been moved to.
     */
    public void setWorld(World world) {
        this.world = world;
    }

    /**
     * Fetches the world the craft is currently registered as being in.
     * @return The world the craft is currently registered as being in.
     */
    public World getWorld() {
        return world;
    }

    /**
     * Sets the player as the craft's pilot.
     * @param player The player to be set as the pilot. Use null to remove but not replace the existing pilot.
     * @return False if you are attempting to set a player as the pilot but the player is not a member of the crew.
     */
    public boolean setPilot(UUID player) {
        if (!isCrewMember(player) && player != null)
            return false;

        pilot = player;
        return true;
    }

    /**
     * Fetches the current pilot of the craft.
     * @return The current pilot of the craft.
     */
    public UUID getPilot() {
        return pilot;
    }

    /**
     * Sets the player as the craft's AA director.
     * @param player The player to be set as the AA director. Use null to remove but not replace the existing AA director.
     * @return False if you are attempting to set a player as the AA director but the player is not a member of the crew.
     */
    public boolean setAADirector(UUID player) {
        if (!isCrewMember(player) && player != null)
            return false;

        AADirector = player;
        return true;
    }

    /**
     * Fetches the current AA director of the craft.
     * @return The current AA director of the craft.
     */
    public UUID getAADirector() {
        return AADirector;
    }

    /**
     * Sets the player as the craft's cannon director.
     * @param player The player to be set as the cannon director. Use null to remove but not replace the existing cannon director.
     * @return False if you are attempting to set a player as the cannon director but the player is not a member of the crew.
     */
    public boolean setCannonDirector(UUID player) {
        if (!isCrewMember(player) && player != null)
            return false;

        cannonDirector = player;
        return true;
    }

    /**
     * Fetches the current cannon director of the craft.
     * @return The current cannon director of the craft.
     */
    public UUID getCannonDirector() {
        return cannonDirector;
    }

    /**
     * Fetches the time of when checks were last performed.
     * @return Time of last checks.
     */
    public long getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * Fetches the time when the craft was piloted.
     * @return Time when craft was piloted.
     */
    public long getOriginalPilotTime() {
        return originalPilotTime;
    }

    /**
     * Fetches the time when the craft last cruised.
     * @return Time when craft last cruised.
     */
    public long getLastCruiseUpdateTime() {
        return lastCruiseUpdateTime;
    }

    /**
     * Fetches the time when the craft last cruised.
     * @param time Time when the craft last cruised.
     */
    public void setLastCruiseUpdateTime(Long time) {
        lastCruiseUpdateTime = time;
    }

    /**
     * Fetches the unique id of the craft.
     * @return Craft's unique id.
     */
    public UUID getID() {
        return id;
    }

    /**
     * <pre>
     *     Sets the current state of the craft.
     *     If the craft is SINKING it cannot be changed.
     * </pre>
     * @param newState The proposed new state of the craft.
     * @return The actual state of the craft.
     */
    public CraftState setState(CraftState newState) {
        if (state == CraftState.SINKING)
            return state;

        if (newState == CraftState.SINKING) {
            CraftSinkEvent event = new CraftSinkEvent(this);
            Sponge.getEventManager().post(event);

            if (event.isCancelled()) {
                return state;
            } else {
                state = CraftState.SINKING;
                return state;
            }
        }

        state = newState;
        return state;

    }

    /**
     * Fetches the current state of the craft.
     * @return Current state of the craft.
     */
    public CraftState getState() {
        return state;
    }

    /**
     * Checks if the craft is not processing.
     * @return False if the craft is processing.
     */
    public boolean isNotProcessing() {
        return !processing.get();
    }

    /**
     * Sets if the craft is currently processing or not.
     */
    public void setProcessing(boolean processing) {
        this.processing.set(processing);
    }

    /**
     * Sets if the craft is in direct control mode.
     * @param setting True if the craft entering direct control mode. False if exiting direct control mode.
     */
    public void setDirectControl(boolean setting) {
        directControl = setting;
    }

    /**
     * Fetches if the craft is in direct control mode or not.
     * @return True if the craft is in direct control mode.
     */
    public boolean underDirectControl() {
        return directControl;
    }

    /**
     * Used to calculate the next translation of the craft while in direct control mode.
     * @param newPilotOffset The combined move requests made by the pilot.
     */
    public void setPilotOffset(Vector3d newPilotOffset) {
        pilotOffset = newPilotOffset;
    }

    /**
     * Fetches the current combined total of the move requests made by the pilot.
     * @return Combined total of the move requests made by the pilot.
     */
    public Vector3d getPilotOffset() {
        return pilotOffset;
    }

    /**
     * Records the craft's last move as a Vector3i.
     * @param lastMoveVector The craft's last move.
     */
    public void setLastMoveVector(Vector3i lastMoveVector) {
        this.lastMoveVector = lastMoveVector;
    }

    /**
     * Fetches the craft's last move.
     * @return The craft's last move.
     */
    public Vector3i getLastMoveVector() {
        return lastMoveVector;
    }

    /**
     * <pre>
     *     Sets the blocks that the craft is currently flying through.
     *     These blocks will be placed back in the world after the craft has flown through them.
     * </pre>
     * @param phasedBlocks The blocks currently being flown through.
     */
    public void setPhasedBlocks(HashSet<BlockSnapshot> phasedBlocks) {
        this.phasedBlocks = phasedBlocks;
    }

    /**
     * Fetches the blocks that the craft is currently flying through.
     * @return The blocks currently being flown through.
     */
    public HashSet<BlockSnapshot> getPhasedBlocks() {
        return phasedBlocks;
    }

    /**
     * Sets the direction of the craft's cruising.
     * @param cruiseDirection The direction the craft will cruise in.
     */
    public void setCruiseDirection(Direction cruiseDirection) {
        this.cruiseDirection = cruiseDirection;
    }

    /**
     * Fetches the direction the craft is currently set to cruise in.
     * @return The direction the craft is currently set to cruise in.
     */
    public Direction getCruiseDirection() {
        return cruiseDirection;
    }

    /**
     * Fetches the number of move points the craft currently has.
     * @return The craft's move points.
     */
    public double getBurningFuel() {
        return burningFuel;
    }

    /**
     * Deducts move points as requested. Used to represent fuel consumption.
     * Will burn fuel from inventories specified in the craft config.
     * @param movePoints The number of move points to deduct.
     * @return TRUE if the operation was successful.
     */
    public boolean burnFuel(double movePoints) {

        if (movePoints < 0)
            return false;

        if (burningFuel >= movePoints) {
            burningFuel -= movePoints;
            return true;
        }

        if (burningFuel < movePoints) {

            //TODO: Edit CraftType configs to allow setting of furnace blocks and fuel items.
            HashSet<Location<World>> furnaceBlocks = new HashSet<>();

            //Find all the furnace blocks
            furnaceBlocks.addAll(findBlockType(BlockTypes.FURNACE));
            furnaceBlocks.addAll(findBlockType(BlockTypes.LIT_FURNACE));

            //Find and burn fuel
            Iterator<Location<World>> workingList = furnaceBlocks.iterator();
            Optional<Furnace> furnaceOptional;
            while (burningFuel < movePoints && workingList.hasNext()) {
                furnaceOptional = workingList.next()
                        .getTileEntity()
                        .filter(Furnace.class::isInstance)
                        .map(Furnace.class::cast);

                if (furnaceOptional.isPresent()) {
                    Inventory furnaceInventory = furnaceOptional.get().getInventory();
                    if (furnaceInventory.contains(ItemTypes.COAL_BLOCK)) {
                        //TODO: Make move points per fuel type configurable.
                        int oldValue = (int) Math.ceil((movePoints - burningFuel) / 72);
                        int newValue = furnaceInventory.query(QueryOperationTypes.ITEM_TYPE.of(ItemTypes.COAL_BLOCK)).poll(oldValue).get().getQuantity();

                        burningFuel += (oldValue - newValue) * 72;

                    } else {
                        int oldValue = (int) Math.ceil((movePoints - burningFuel) / 8);
                        int newValue = furnaceInventory.query(QueryOperationTypes.ITEM_TYPE.of(ItemTypes.COAL)).poll(oldValue).get().getQuantity();

                        burningFuel += (oldValue - newValue) * 8;
                    }
                }
            }

            if (burningFuel >= movePoints) {
                burningFuel -= movePoints;
                return true;
            }
        }

        return false;
    }

    /**
     * Calculates the fuel that has not yet been burned.
     * @return The total unburned move points currently aboard the craft.
     */
    public int checkFuelStored() {

        int fuelStored = 0;
        HashSet<Location<World>> furnaceBlocks = new HashSet<>();

        //Find all the furnace blocks
        furnaceBlocks.addAll(findBlockType(BlockTypes.FURNACE));
        furnaceBlocks.addAll(findBlockType(BlockTypes.LIT_FURNACE));

        //Find fuel
        Iterator<Location<World>> workingList = furnaceBlocks.iterator();
        Optional<Furnace> furnaceOptional;
        Inventory furnaceInventory;

        while (workingList.hasNext()) {
            furnaceOptional = workingList.next()
                    .getTileEntity()
                    .filter(Furnace.class::isInstance)
                    .map(Furnace.class::cast);

            if (furnaceOptional.isPresent()) {
                furnaceInventory = furnaceOptional.get().getInventory();

                fuelStored += furnaceInventory.query(QueryOperationTypes.ITEM_TYPE.of(ItemTypes.COAL_BLOCK)).totalItems() * 72;
                fuelStored += furnaceInventory.query(QueryOperationTypes.ITEM_TYPE.of(ItemTypes.COAL)).totalItems() * 8;
            }
        }

        return fuelStored;
    }

    /**
     * Fetches the number of moves the craft has made so far.
     * @return Number of times the craft has moved.
     */
    public int getNumberOfMoves() {
        return numberOfMoves;
    }

    /**
     *
     * @param blockType
     * @return
     */
    public HashSet<Location<World>> findBlockType(BlockType blockType) {
        HashSet<Location<World>> foundBlocks = new HashSet<>();
        getHitBox().forEach(mLoc -> {
            if (getWorld().getBlockType(mLoc) == blockType)
                foundBlocks.add(getWorld().getLocation(mLoc));
        });

        return foundBlocks;
    }

    /**
     *
     * @return
     */
    public UUID getOriginalPilot() {
        return originalPilot;
    }

    //--------------------------//

    public void translate(Rotation rotation, Vector3i moveVector, boolean isSubCraft) {

        if (rotation == Rotation.NONE) {

            // check to see if the craft is trying to move in a direction not permitted by the type
            if (!this.getType().allowHorizontalMovement() && this.getState() != CraftState.SINKING) {
                moveVector = new Vector3i(0, moveVector.getY(), 0);
            }
            if (!this.getType().allowVerticalMovement() && this.getState() != CraftState.SINKING) {
                moveVector = new Vector3i(moveVector.getX(), 0, moveVector.getZ());
            }
            if (moveVector.getX() == 0 && moveVector.getY() == 0 && moveVector.getZ() == 0) {
                return;
            }

            if (!this.getType().allowVerticalTakeoffAndLanding() && moveVector.getY() != 0 && this.getState() != CraftState.SINKING) {
                if (moveVector.getX() == 0 && moveVector.getZ() == 0) {
                    return;
                }
            }

            Movecraft.getInstance().getAsyncManager().submitTask(new TranslationTask(this, moveVector), this);

        } else if (!isSubCraft) {

            if(getLastRotateTime()+1e9>System.nanoTime()){
                if(getPilot()!= null)
                    Sponge.getServer().getPlayer(getPilot()).ifPresent(player -> player.sendMessage(Text.of("You're turning too quickly!")));
                return;
            }
            setLastRotateTime(System.nanoTime());
            Movecraft.getInstance().getAsyncManager().submitTask(new RotationTask(this, moveVector, rotation, this.getWorld(), isSubCraft), this);

        } else {

            Movecraft.getInstance().getAsyncManager().submitTask(new RotationTask(this, moveVector, rotation, this.getWorld(), isSubCraft), this);

        }
    }

    public int getWaterLine(){
        //TODO: Remove this temporary system in favor of passthrough blocks
        // Find the waterline from the surrounding terrain or from the static level in the craft type
        int waterLine = 0;
        if (currentHitbox.isEmpty())
            return waterLine;

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