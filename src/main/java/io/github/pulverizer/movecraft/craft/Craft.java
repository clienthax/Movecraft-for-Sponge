package io.github.pulverizer.movecraft.craft;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.CraftState;
import io.github.pulverizer.movecraft.events.CraftSinkEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Craft {

    //Facts
    private final CraftType type;
    private final UUID id = UUID.randomUUID();
    private HashHitBox initialHitbox;
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
    private Set<Player> crewList = new HashSet<>();


    //Direct Control
    private boolean directControl = false;
    private Vector3d pilotOffset;


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

    /**
     * Initialises the craft.
     * @param type The type of the craft.
     * @param world The world the craft is currently in.
     */
    public Craft(CraftType type, World world) {
        this.type = type;
        setWorld(world);
        setLastCruiseUpdateTime(System.currentTimeMillis() - 10000);
        this.originalPilotTime = System.currentTimeMillis();
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
    public void addCrewMember(Player player) {
        crewList.add(player);
    }

    /**
     * Removes the player from the craft's crew.
     * @param player The player to be removed.
     */
    public void removeCrewMember(Player player) {
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
    public Set<Player> getCrewList() {
        return crewList;
    }

    /**
     * Checks if the player is a member of the craft's crew.
     * @param player Player to look for.
     * @return True if the player is a part of the crew.
     */
    public boolean isCrewMember(Player player) {
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

    //TODO: Fix this mess. initialHitbox should be set on initialisation of the Craft.
    /**
     * Creates the initial hitbox of the craft. This method can only be used once and should only be called by DetectionTask.
     * @param detectedHitbox The first ever hitbox of the craft.
     * @return False if the initial hitbox has already been set.
     */
    public boolean setInitialHitbox(HashHitBox detectedHitbox) {
        if (!initialHitbox.isEmpty())
            return false;

        initialHitbox = detectedHitbox;
        setHitbox(detectedHitbox);
        return true;
    }

    /**
     * Fetches the initial hitbox of the craft.
     * @return The initial hitbox of the craft.
     */
    public HashHitBox getInitialHitbox() {
        return initialHitbox;
    }

    /**
     * Fetches the initial size of the craft.
     * @return The initial size of the craft.
     */
    public int getInitialSize() {
        return initialHitbox.size();
    }

    /**
     * Sets the current hitbox of the craft.
     * @param newHitbox The craft's new hitbox.
     */
    public void setHitbox(HashHitBox newHitbox) {
        currentHitbox = newHitbox;
    }

    /**
     * Fetches the craft's current hitbox.
     * @return The craft's current hitbox.
     */
    public HashHitBox getHitbox() {
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
    public boolean setPilot(Player player) {
        if (!isCrewMember(player) && player != null)
            return false;

        pilot = player;
        return true;
    }

    /**
     * Fetches the current pilot of the craft.
     * @return The current pilot of the craft.
     */
    public Player getPilot() {
        return pilot;
    }

    /**
     * Sets the player as the craft's AA director.
     * @param player The player to be set as the AA director. Use null to remove but not replace the existing AA director.
     * @return False if you are attempting to set a player as the AA director but the player is not a member of the crew.
     */
    public boolean setAADirector(Player player) {
        if (!isCrewMember(player) && player != null)
            return false;

        AADirector = player;
        return true;
    }

    /**
     * Fetches the current AA director of the craft.
     * @return The current AA director of the craft.
     */
    public Player getAADirector() {
        return AADirector;
    }

    /**
     * Sets the player as the craft's cannon director.
     * @param player The player to be set as the cannon director. Use null to remove but not replace the existing cannon director.
     * @return False if you are attempting to set a player as the cannon director but the player is not a member of the crew.
     */
    public boolean setCannonDirector(Player player) {
        if (!isCrewMember(player) && player != null)
            return false;

        cannonDirector = player;
        return true;
    }

    /**
     * Fetches the current cannon director of the craft.
     * @return The current cannon director of the craft.
     */
    public Player getCannonDirector() {
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
     * Fetches the current total offset of the pilot.
     * @return
     */
    public Vector3d getPilotOffset() {
        return pilotOffset;
    }

    /**
     *
     * @param lastMoveVector
     */
    public void setLastMoveVector(Vector3i lastMoveVector) {
        this.lastMoveVector = lastMoveVector;
    }

    /**
     *
     * @return
     */
    public Vector3i getLastMoveVector() {
        return lastMoveVector;
    }

    //--------------------------//

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