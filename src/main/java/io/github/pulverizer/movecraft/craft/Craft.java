package io.github.pulverizer.movecraft.craft;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.async.AsyncTask;
import io.github.pulverizer.movecraft.async.DetectionTask;
import io.github.pulverizer.movecraft.async.RotationTask;
import io.github.pulverizer.movecraft.async.TranslationTask;
import io.github.pulverizer.movecraft.config.CraftType;
import io.github.pulverizer.movecraft.enums.Rotation;
import io.github.pulverizer.movecraft.event.CraftSinkEvent;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.block.tileentity.carrier.TileEntityCarrier;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.explosive.PrimedTNT;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.projectile.explosive.fireball.SmallFireball;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a player controlled craft
 *
 * @author BernardisGood
 * @version 0.8 - 20 Apr 2020
 */
public class Craft {

    // Facts
    private final CraftType type;
    private final UUID id = UUID.randomUUID();
    private final UUID commandeeredBy;
    private int initialSize;
    private final long commandeeredAt;
    private final int maxHeightLimit;
    private boolean isSubCraft;


    // State
    private String name;
    private final AtomicBoolean processing = new AtomicBoolean();
    private int processingStartTime = 0;
    private HashHitBox hitBox;
    // TODO - Sinking related - Need to implement
    //  makes it so the craft can't collapse on itself
    //  CollapsedHitbox is to prevent the wrecks from despawning when sinking into water
    protected final HashHitBox collapsedHitBox = new HashHitBox();
    private long lastCheckTick = 0L;
    private World world; //TODO - Make cross-dimension travel possible
    private boolean sinking = false;
    private boolean disabled = false;


    // Crew
    private UUID commander;
    private UUID nextInCommand;
    private UUID pilot;
    private final HashSet<UUID> aaDirectors = new HashSet<>();
    private final HashSet<UUID> cannonDirectors = new HashSet<>();
    private final HashSet<UUID> loaders = new HashSet<>(); //TODO - Implement
    private final HashSet<UUID> repairmen = new HashSet<>(); //TODO - Implement
    private final HashSet<UUID> crewList = new HashSet<>();


    // Movement
    private Vector3i lastMoveVector = new Vector3i();
    @Deprecated
    private HashSet<BlockSnapshot> phasedBlocks = new HashSet<>(); //TODO - move to listener database thingy
    private double movePoints;
    private int numberOfMoves = 0;
    private float meanMoveTime;
    //   Cruising
    private int lastMoveTick;
    private Direction verticalCruiseDirection = Direction.NONE;
    private Direction horizontalCruiseDirection = Direction.NONE;
    //   Rotating
    private long lastRotateTime = 0;


    // Direct Control
    //TODO - Needs rewriting/implementing
    private boolean directControl = false;
    private Vector3d pilotOffset = new Vector3d(0, 0, 0);
    private boolean pilotLocked = false;
    private double pilotLockedX = 0.0;
    private double pilotLockedY = 0.0;
    private double pilotLockedZ = 0.0;


    /**
     * Initialises the craft and detects the craft's hitbox.
     *
     * @param type          The type of craft to detect
     * @param player        The player that triggered craft detection
     * @param startLocation The location from which to start craft detection
     */
    public Craft(CraftType type, UUID player, Location<World> startLocation, boolean isSubCraft) {
        this.type = type;
        world = startLocation.getExtent();
        this.isSubCraft = isSubCraft;

        lastMoveTick = Sponge.getServer().getRunningTimeTicks() - 100;
        commandeeredAt = System.currentTimeMillis();
        commandeeredBy = player;
        maxHeightLimit = Math.min(type.getMaxHeightLimit(), world.getDimension().getBuildHeight() - 1);

        submitTask(new DetectionTask(this, startLocation));
    }


    // Facts
    public CraftType getType() {
        return type;
    }

    public UUID getId() {
        return id;
    }

    public UUID commandeeredBy() {
        return commandeeredBy;
    }

    public int getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(int updatedInitialSize) {
        initialSize = updatedInitialSize;
    }

    public long commandeeredAt() {
        return commandeeredAt;
    }

    public int getMaxHeightLimit() {
        return maxHeightLimit;
    }

    public boolean isSubCraft() {
        return isSubCraft;
    }

    public void setSubCraft(boolean isSubCraft) {
        this.isSubCraft = isSubCraft;
    }


    // State

    public String getName() {
        return name;
    }

    public void setName(String newName) {
        name = newName;
    }

    public boolean isNotProcessing() {
        return !processing.get();
    }

    public void setProcessing(boolean isProcessing) {
        processingStartTime = Sponge.getServer().getRunningTimeTicks();
        processing.set(isProcessing);
    }

    public int getProcessingStartTime() {
        return processingStartTime;
    }

    public HashHitBox getHitBox() {
        return hitBox;
    }

    public void setHitBox(HashHitBox newHitBox) {
        hitBox = newHitBox;
    }

    public int getSize() {
        return hitBox.size();
    }

    public long getLastCheckTick() {
        return lastCheckTick;
    }

    //TODO - Replace with runChecks() and maybe use an event and listener so people can add their own checks?
    public void updateLastCheckTick() {
        lastCheckTick = Sponge.getServer().getRunningTimeTicks();
    }

    public World getWorld() {
        return world;
    }

    public boolean isSinking() {
        return sinking;
    }

    public void sink() {
        // Throw an event
        CraftSinkEvent event = new CraftSinkEvent(this);
        Sponge.getEventManager().post(event);

        // Check if the event has been cancelled
        if (event.isCancelled()) {
            return;
        }

        // The event was not cancelled
        // Change the craft's state
        sinking = true;


        // And notify the crew
        notifyCrew("Craft is sinking!");
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void disable() {
        //TODO - Create an event for this
        disabled = true;
    }

    public void enable() {
        //TODO - Create an event for this
        disabled = false;
    }


    // Crew

    public UUID getCommander() {
        return commander;
    }

    public boolean setCommander(UUID player) {
        if ((crewList.contains(player) || player == commandeeredBy) && player != null) {
            commander = player;

            if (player == nextInCommand || nextInCommand == null) resetNextInCommand();
            return true;
        }

        return false;
    }

    public UUID getNextInCommand() {
        return nextInCommand;
    }

    public boolean setNextInCommand(UUID player) {
        if (crewList.contains(player) && player != null) {
            nextInCommand = player;
            return true;
        }

        return false;
    }

    public void resetNextInCommand() {
        nextInCommand = null;

        //TODO - Notify commander that it is recommended to assign a Next-In-Command
    }

    public UUID getPilot() {
        return pilot;
    }

    public boolean setPilot(UUID player) {
        if (crewList.contains(player) && player != null) {
            resetCrewRole(player);

            pilot = player;
            return true;
        }

        return false;
    }

    public boolean isAADirector(UUID player) {
        return aaDirectors.contains(player);
    }

    public boolean addAADirector(UUID player) {
        if (crewList.contains(player)) {
            resetCrewRole(player);

            aaDirectors.add(player);
            return true;
        }

        return false;
    }

    public Player getAADirectorFor(SmallFireball fireball) {
        Player player = null;
        double distance = 16 * 64;
        HashSet<UUID> testSet = new HashSet<>(aaDirectors);

        for (UUID testUUID : testSet) {
            Player testPlayer = Sponge.getServer().getPlayer(testUUID).orElse(null);

            if (testPlayer == null) {
                CraftManager.getInstance().removePlayer(testUUID);
                continue;
            }

            //TODO - Add test for matching facing direction
            double testDistance = testPlayer.getLocation().getPosition().distance(fireball.getLocation().getPosition());

            if (testDistance < distance) {
                player = testPlayer;
                distance = testDistance;
            }
        }

        return player;
    }

    public boolean isCannonDirector(UUID player) {
        return cannonDirectors.contains(player);
    }

    public boolean addCannonDirector(UUID player) {
        if (crewList.contains(player)) {
            resetCrewRole(player);

            cannonDirectors.add(player);
            return true;
        }

        return false;
    }

    public Player getCannonDirectorFor(PrimedTNT primedTNT) {
        Player player = null;
        double distance = 16 * 64;
        HashSet<UUID> testSet = new HashSet<>(aaDirectors);

        for (UUID testUUID : testSet) {
            Player testPlayer = Sponge.getServer().getPlayer(testUUID).orElse(null);

            if (testPlayer == null) {
                CraftManager.getInstance().removePlayer(testUUID);
                continue;
            }

            //TODO - Add test for matching facing direction
            double testDistance = testPlayer.getLocation().getPosition().distance(primedTNT.getLocation().getPosition());

            if (testDistance < distance) {
                player = testPlayer;
                distance = testDistance;
            }
        }

        return player;
    }

    public boolean isLoader(UUID player) {
        return loaders.contains(player);
    }

    public boolean addLoader(UUID player) {
        if (crewList.contains(player)) {
            resetCrewRole(player);

            loaders.add(player);
            return true;
        }

        return false;
    }

    public boolean isRepairman(UUID player) {
        return repairmen.contains(player);
    }

    public boolean addRepairman(UUID player) {
        if (crewList.contains(player)) {
            resetCrewRole(player);

            repairmen.add(player);
            return true;
        }

        return false;
    }

    public boolean crewIsEmpty() {
        return crewList.isEmpty();
    }

    public boolean isCrewMember(UUID player) {
        return crewList.contains(player);
    }

    public void addCrewMember(UUID player) {
        crewList.add(player);
    }

    public void removeCrewMember(UUID player) {
        if (player == null) return;

        if (player == commander && nextInCommand != null) setCommander(nextInCommand);

        if (player == nextInCommand) resetNextInCommand();

        resetCrewRole(player);
        crewList.remove(player);
    }

    public void resetCrewRole(UUID player) {
        if (pilot == player) pilot = null;
        aaDirectors.remove(player);
        cannonDirectors.remove(player);
        loaders.remove(player);
        repairmen.remove(player);
    }

    public void removeCrew() {
        commander = null;
        nextInCommand = null;
        pilot = null;
        aaDirectors.clear();
        cannonDirectors.clear();
        loaders.clear();
        repairmen.clear();
        crewList.clear();
    }

    public void notifyCrew(String message) {
        //TODO - Add Main Config setting to decide if the crew should be notified of such events
        crewList.forEach(crewMember -> Sponge.getServer().getPlayer(crewMember).ifPresent(player -> player.sendMessage(Text.of(message))));
    }


    // Movement

    public Vector3i getLastMoveVector() {
        return lastMoveVector;
    }

    public void setLastMoveVector(Vector3i vector) {
        lastMoveVector = vector;
    }

    @Deprecated
    public HashSet<BlockSnapshot> getPhasedBlocks() {
        return phasedBlocks;
    }

    public double getMovePoints() {
        return movePoints;
    }

    public boolean useFuel(double requiredPoints) {

        if (requiredPoints < 0)
            return false;

        if (movePoints < requiredPoints) {

            HashSet<Vector3i> furnaceBlocks = new HashSet<>();
            Map<BlockType, Set<Vector3i>> blockMap = hitBox.map(world);

            //Find all the furnace blocks
            getType().getFurnaceBlocks().forEach(blockType -> {
                if (blockMap.containsKey(blockType)) {
                    furnaceBlocks.addAll(blockMap.get(blockType));
                }
            });

            //Find and burn fuel
            for (Vector3i furnaceLocation : furnaceBlocks) {
                if (world.getTileEntity(furnaceLocation).isPresent() && world.getTileEntity(furnaceLocation).get() instanceof TileEntityCarrier) {
                    Inventory inventory = ((TileEntityCarrier) world.getTileEntity(furnaceLocation).get()).getInventory();

                    Set<ItemType> fuelItems = getType().getFuelItems().keySet();

                    for (ItemType fuelItem : fuelItems) {
                        if (inventory.contains(fuelItem)) {

                            double fuelItemValue = getType().getFuelItems().get(fuelItem);

                            int oldValue = (int) Math.ceil((requiredPoints - movePoints) / fuelItemValue);
                            int newValue = inventory.query(QueryOperationTypes.ITEM_TYPE.of(fuelItem)).poll(oldValue).get().getQuantity();

                            movePoints += newValue * fuelItemValue;
                        }

                        if (movePoints >= requiredPoints)
                            break;
                    }
                }

                if (movePoints >= requiredPoints)
                    break;

            }
        }

        if (movePoints >= requiredPoints) {
            movePoints -= requiredPoints;
            return true;
        }

        return false;
    }

    public int checkFuelStored() {

        int fuelStored = 0;
        HashSet<Vector3i> furnaceBlocks = new HashSet<>();
        Map<BlockType, Set<Vector3i>> blockMap = hitBox.map(world);

        //Find all the furnace blocks
        getType().getFurnaceBlocks().forEach(blockType -> {
            if (blockMap.containsKey(blockType)) {
                furnaceBlocks.addAll(blockMap.get(blockType));
            }
        });

        //Find and count the fuel
        for (Vector3i furnaceLocation : furnaceBlocks) {
            if (world.getTileEntity(furnaceLocation).isPresent() && world.getTileEntity(furnaceLocation).get() instanceof TileEntityCarrier) {
                Inventory inventory = ((TileEntityCarrier) world.getTileEntity(furnaceLocation).get()).getInventory();

                Set<ItemType> fuelItems = getType().getFuelItems().keySet();

                for (ItemType fuelItem : fuelItems) {
                    if (inventory.contains(fuelItem)) {

                        fuelStored += inventory.query(QueryOperationTypes.ITEM_TYPE.of(fuelItem)).totalItems() * getType().getFuelItems().get(fuelItem);

                    }
                }
            }
        }

        return fuelStored;
    }

    public int getNumberOfMoves() {
        return numberOfMoves;
    }

    public float getMeanMoveTime() {
        return meanMoveTime;
    }

    public void addMoveTime(float moveTime) {
        meanMoveTime = (meanMoveTime * numberOfMoves + moveTime) / (++numberOfMoves);
    }

    public int getLastMoveTick() {
        return lastMoveTick;
    }

    public void updateLastMoveTick() {
        lastMoveTick = Sponge.getServer().getRunningTimeTicks();
    }

    public void setCruising(Direction vertical, Direction horizontal) {

        if (vertical != Direction.UP
                && vertical != Direction.NONE
                && vertical != Direction.DOWN) {
            return;
        }
        if (horizontal != Direction.NONE
                && horizontal != Direction.NORTH
                && horizontal != Direction.WEST
                && horizontal != Direction.SOUTH
                && horizontal != Direction.EAST) {
            return;
        }

        if (pilot != null) {
            Sponge.getServer().getPlayer(pilot).ifPresent(player -> player.sendMessage(ChatTypes.ACTION_BAR, Text.of("Cruising " + ((vertical != Direction.NONE || horizontal != Direction.NONE) ? "Enabled" : "Disabled"))));
        }

        horizontalCruiseDirection = horizontal;
        verticalCruiseDirection = vertical;

        // Change signs aboard the craft to reflect new cruising state
        resetSigns();
    }

    public Direction getVerticalCruiseDirection() {
        return verticalCruiseDirection;
    }

    public Direction getHorizontalCruiseDirection() {
        return horizontalCruiseDirection;
    }

    public boolean isCruising() {
        return verticalCruiseDirection != Direction.NONE || horizontalCruiseDirection != Direction.NONE;
    }

    private void resetSigns() {
        hitBox.forEach(loc -> {
            final TileEntity tileEntity = world.getTileEntity(loc).orElse(null);

            if (!(tileEntity instanceof Sign)) {
                return;
            }

            final Sign sign = (Sign) tileEntity;

            ListValue<Text> signLines = sign.lines();

            // Reset ALL signs
            // Do Vertical first
            if (signLines.get(0).toPlain().equalsIgnoreCase("Ascend: ON") && verticalCruiseDirection != Direction.UP) {
                signLines.set(0, Text.of("Ascend: OFF"));

            } else if (signLines.get(0).toPlain().equalsIgnoreCase("Ascend: OFF") && verticalCruiseDirection == Direction.UP) {
                signLines.set(0, Text.of("Ascend: ON"));

            } else if (signLines.get(0).toPlain().equalsIgnoreCase("Descend: ON") && verticalCruiseDirection != Direction.DOWN) {
                signLines.set(0, Text.of("Descend: OFF"));

            } else if (signLines.get(0).toPlain().equalsIgnoreCase("Descend: OFF") && verticalCruiseDirection == Direction.DOWN) {
                signLines.set(0, Text.of("Descend: ON"));

                // Then do Horizontal
            } else if (signLines.get(0).toPlain().equalsIgnoreCase("Cruise: ON") && sign.getBlock().get(Keys.DIRECTION).get() != horizontalCruiseDirection) {
                signLines.set(0, Text.of("Cruise: OFF"));

            } else if (signLines.get(0).toPlain().equalsIgnoreCase("Cruise: OFF") && sign.getBlock().get(Keys.DIRECTION).get() == horizontalCruiseDirection) {
                signLines.set(0, Text.of("Cruise: ON"));
            }

            sign.offer(signLines);
        });
    }

    //TODO - Add code to handle SubCrafts
    public void translate(Vector3i displacement) {
        // check to see if the craft is trying to move in a direction not permitted by the type
        if (!this.getType().allowHorizontalMovement() && !sinking) {
            displacement = new Vector3i(0, displacement.getY(), 0);
        }

        if (!this.getType().allowVerticalMovement() && !sinking) {
            displacement = new Vector3i(displacement.getX(), 0, displacement.getZ());
        }

        if (displacement.getX() == 0 && displacement.getY() == 0 && displacement.getZ() == 0) {
            return;
        }

        if (!this.getType().allowVerticalTakeoffAndLanding() && displacement.getY() != 0 && !sinking) {
            if (displacement.getX() == 0 && displacement.getZ() == 0) {
                return;
            }
        }

        submitTask(new TranslationTask(this, new Vector3i(displacement.getX(), displacement.getY(), displacement.getZ())));
    }

    public void rotate(Vector3i originPoint, Rotation rotation) {
        if (lastRotateTime + 1e9 > System.nanoTime() && !isSubCraft) {
            if (pilot != null)
                Sponge.getServer().getPlayer(pilot).ifPresent(player -> player.sendMessage(Text.of("Rotation - Turning Too Quickly")));
            return;
        }

        lastRotateTime = System.nanoTime();
        submitTask(new RotationTask(this, originPoint, rotation, world));
    }


    // Direct Control

    /**
     * Sets if the craft is in direct control mode.
     *
     * @param setting TRUE - If the craft entering direct control mode. False if exiting direct control mode.
     */
    public void setDirectControl(boolean setting) {
        directControl = setting;
    }

    /**
     * Fetches if the craft is in direct control mode or not.
     *
     * @return TRUE - If the craft is in direct control mode.
     */
    public boolean isUnderDirectControl() {
        return directControl;
    }

    // OLD STUFF -------------------------------------------------------------------------
    //TODO - Sort these methods
    public double getSpeed() {
        return Sponge.getServer().getTicksPerSecond() * type.getCruiseSkipBlocks() / (double) getTickCooldown();
    }

    public int getTickCooldown() {
        //TODO Rewrite this
        return type.getTickCooldown();

        /*
        Map<BlockType, Set<Vector3i>> blockMap = hitBox.map(world);

        if(state == CraftState.SINKING)
            return type.getSinkRateTicks();

        double chestPenalty = 0;

        chestPenalty += (blockMap.containsKey(BlockTypes.CHEST) ? blockMap.get(BlockTypes.CHEST).size() : 0)
                + (blockMap.containsKey(BlockTypes.TRAPPED_CHEST) ? blockMap.get(BlockTypes.TRAPPED_CHEST).size() : 0);

        chestPenalty *= type.getChestPenalty();

        if(meanMoveTime == 0)
            return type.getCruiseTickCooldown() + (int) chestPenalty;

        if(state != CraftState.CRUISING)
            return type.getTickCooldown() + (int) chestPenalty;

        if(type.getDynamicFlyBlockSpeedFactor() != 0){
            double count = blockMap.get(type.getDynamicFlyBlock()).size();


            return Math.max((int) (20 / (type.getCruiseTickCooldown() * (1  + type.getDynamicFlyBlockSpeedFactor() * (count / hitBox.size() - 0.5)))), 1);

        }

        if(type.getDynamicLagSpeedFactor() == 0)
            return type.getCruiseTickCooldown() + (int) chestPenalty;

        //TODO: modify skip blocks by an equal proportion to this, than add another modifier based on dynamic speed factor
        return Math.max((int)(type.getCruiseTickCooldown() * meanMoveTime / 1000 * 20 / type.getDynamicLagSpeedFactor() + chestPenalty), 1);
         */
    }

    public Set<Craft> getContacts() {
        final Set<Craft> contacts = new HashSet<>();
        for (Craft contact : CraftManager.getInstance().getCraftsInWorld(world)) {
            Vector3i ccenter = this.getHitBox().getMidPoint();
            Vector3i tcenter = contact.getHitBox().getMidPoint();
            int distsquared = ccenter.distanceSquared(tcenter);
            int detectionRange = (int) (contact.getInitialSize() * (tcenter.getY() > 65 ? contact.getType().getDetectionMultiplier() : contact.getType().getUnderwaterDetectionMultiplier()));
            detectionRange = detectionRange * 10;
            if (distsquared > detectionRange || contact.getCommander() == this.getCommander()) {
                continue;
            }
            contacts.add(contact);
        }
        return contacts;
    }

    public int getWaterLine() {
        //TODO: Remove this temporary system in favor of passthrough blocks. How tho???
        // Find the waterline from the surrounding terrain or from the static level in the craft type
        int waterLine = 0;
        if (hitBox.isEmpty())
            return waterLine;

        // figure out the water level by examining blocks next to the outer boundaries of the craft
        for (int posY = hitBox.getMaxY() + 1; posY >= hitBox.getMinY() - 1; posY--) {
            int numWater = 0;
            int numAir = 0;
            int posX;
            int posZ;
            posZ = hitBox.getMinZ() - 1;
            for (posX = hitBox.getMinX() - 1; posX <= hitBox.getMaxX() + 1; posX++) {
                BlockType typeID = world.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posZ = hitBox.getMaxZ() + 1;
            for (posX = hitBox.getMinX() - 1; posX <= hitBox.getMaxX() + 1; posX++) {
                BlockType typeID = world.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posX = hitBox.getMinX() - 1;
            for (posZ = hitBox.getMinZ(); posZ <= hitBox.getMaxZ(); posZ++) {
                BlockType typeID = world.getBlock(posX, posY, posZ).getType();
                if (typeID == BlockTypes.WATER)
                    numWater++;
                if (typeID == BlockTypes.AIR)
                    numAir++;
            }
            posX = hitBox.getMaxX() + 1;
            for (posZ = hitBox.getMinZ(); posZ <= hitBox.getMaxZ(); posZ++) {
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

    public void submitTask(AsyncTask task) {
        if (isNotProcessing()) {
            setProcessing(true);
            task.run();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Craft)) {
            return false;
        }
        return this.id.equals(((Craft) obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}