package io.github.pulverizer.movecraft.async;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableSet;
import io.github.pulverizer.movecraft.enums.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.event.CraftTranslateEvent;
import io.github.pulverizer.movecraft.mapUpdater.MapUpdateManager;
import io.github.pulverizer.movecraft.mapUpdater.update.*;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class TranslationTask extends AsyncTask {
    
    //TODO: Move to config.
    private static final ImmutableSet<BlockType> FALL_THROUGH_BLOCKS = ImmutableSet.of(BlockTypes.AIR, BlockTypes.FLOWING_WATER, BlockTypes.FLOWING_LAVA, BlockTypes.TALLGRASS, BlockTypes.YELLOW_FLOWER, BlockTypes.RED_FLOWER, BlockTypes.BROWN_MUSHROOM, BlockTypes.RED_MUSHROOM, BlockTypes.TORCH, BlockTypes.FIRE, BlockTypes.REDSTONE_WIRE, BlockTypes.WHEAT, BlockTypes.STANDING_SIGN, BlockTypes.LADDER, BlockTypes.WALL_SIGN, BlockTypes.LEVER, BlockTypes.LIGHT_WEIGHTED_PRESSURE_PLATE, BlockTypes.HEAVY_WEIGHTED_PRESSURE_PLATE, BlockTypes.STONE_PRESSURE_PLATE, BlockTypes.WOODEN_PRESSURE_PLATE, BlockTypes.UNLIT_REDSTONE_TORCH, BlockTypes.REDSTONE_TORCH, BlockTypes.STONE_BUTTON, BlockTypes.SNOW_LAYER, BlockTypes.REEDS, BlockTypes.FENCE, BlockTypes.ACACIA_FENCE, BlockTypes.BIRCH_FENCE, BlockTypes.DARK_OAK_FENCE, BlockTypes.JUNGLE_FENCE, BlockTypes.NETHER_BRICK_FENCE, BlockTypes.SPRUCE_FENCE, BlockTypes.UNPOWERED_REPEATER, BlockTypes.POWERED_REPEATER, BlockTypes.WATERLILY, BlockTypes.CARROTS, BlockTypes.POTATOES, BlockTypes.WOODEN_BUTTON, BlockTypes.CARPET);

    private Vector3i moveVector;
    private HashHitBox newHitBox, oldHitBox;
    private boolean failed;
    private boolean collisionExplosion = false;
    private String failMessage;
    private Collection<UpdateCommand> updates = new HashSet<>();
    private World world;

    private final List<BlockType> harvestBlocks = craft.getType().getHarvestBlocks();
    private final List<Vector3i> harvestedBlocks = new ArrayList<>();
    private final List<BlockType> harvesterBladeBlocks = craft.getType().getHarvesterBladeBlocks();
    private final HashHitBox collisionBox = new HashHitBox();

    public TranslationTask(Craft craft, Vector3i moveVector) {
        super(craft, "Translation");
        world = craft.getWorld();
        this.moveVector = moveVector;
        newHitBox = new HashHitBox();
        oldHitBox = new HashHitBox(craft.getHitBox());
    }

    @Override
    protected void execute() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        if (oldHitBox.isEmpty() || craft.getState() == CraftState.DISABLED)
            return;

        if (!checkCraftHeight()) {
            return;
        }

        // check for fuel and burn some if needed.
        double fuelBurnRate = getCraft().getType().getFuelBurnRate();
        if (fuelBurnRate != 0.0 && getCraft().getState() != CraftState.SINKING) {

            boolean fuelBurned = getCraft().burnFuel(fuelBurnRate);

            if (!fuelBurned) {
                fail("Translation Failed - Craft out of fuel");
                return;
            }
        }

        if(craftObstructed())
            return;

        //call event
        CraftTranslateEvent event = new CraftTranslateEvent(craft, oldHitBox, newHitBox);
        Sponge.getEventManager().post(event);
        if(event.isCancelled()){
            this.fail(event.getFailMessage());
            return;
        }

        //-------------------------------------//

        if(craft.getState() == CraftState.SINKING){
            for(Vector3i location : collisionBox){
                if (craft.getType().getExplodeOnCrash() > 0.0F) {

                    if (!world.getBlockType(location).equals(BlockTypes.AIR)  && ThreadLocalRandom.current().nextDouble(1) < .05) {
                        updates.add(new ExplosionUpdateCommand( world, location, craft.getType().getExplodeOnCrash()));
                        collisionExplosion=true;
                    }
                }

                List<Vector3i> toRemove = new ArrayList<>();
                Vector3i next = location;

                do {
                    toRemove.add(next);
                    next = next.add(new Vector3i(0,1,0));
                }while (newHitBox.contains(next));

                newHitBox.removeAll(toRemove);
            }

        }else{
            for(Vector3i location : collisionBox){
                if (!(craft.getType().getCollisionExplosion() != 0.0F) || System.currentTimeMillis() - craft.getOriginalPilotTime() <= 1000) {
                    continue;
                }
                float explosionKey;
                float explosionForce = craft.getType().getCollisionExplosion();
                if (craft.getType().getFocusedExplosion()) {
                    explosionForce *= oldHitBox.size();
                }
                //TODO: Account for underwater explosions
                /*if (location.getY() < waterLine) { // underwater explosions require more force to do anything
                    explosionForce += 25;//TODO: find the correct amount
                }*/
                explosionKey = explosionForce;
                if (!world.getBlockType(location).equals(BlockTypes.AIR)) {
                    updates.add(new ExplosionUpdateCommand(world, location, explosionKey));
                    collisionExplosion = true;
                }
                if (craft.getType().getFocusedExplosion()) { // don't handle any further collisions if it is set to focusedexplosion
                    break;
                }
            }
        }

        if(!collisionBox.isEmpty() && craft.getType().getCruiseOnPilot()){
            CraftManager.getInstance().removeCraft(craft);
            for(Vector3i location : oldHitBox){
                updates.add(new BlockCreateCommand(craft.getWorld(), location, BlockTypes.AIR));
            }
            newHitBox = new HashHitBox();
        }

        updates.add(new CraftTranslateCommand(craft, new Vector3i(moveVector.getX(), moveVector.getY(), moveVector.getZ()), getNewHitBox()));

        //prevents torpedo and rocket pilots
        if (craft.getType().getMoveEntities() && craft.getState() != CraftState.SINKING) {

            if (Settings.Debug)
                Movecraft.getInstance().getLogger().info("Craft moves Entities.");

            AtomicBoolean processedEntities = new AtomicBoolean(false);

            Task.builder()
                    .execute(() -> {
                        for(Entity entity : craft.getWorld().getIntersectingEntities(new AABB(oldHitBox.getMinX() - 0.5, oldHitBox.getMinY() - 0.5, oldHitBox.getMinZ() - 0.5, oldHitBox.getMaxX() + 1.5, oldHitBox.getMaxY() + 1.5, oldHitBox.getMaxZ()+1.5))){

                            if (entity.getType() == EntityTypes.PLAYER || entity.getType() == EntityTypes.PRIMED_TNT || entity.getType() == EntityTypes.ITEM || !craft.getType().getOnlyMovePlayers()) {
                                EntityUpdateCommand eUp = new EntityUpdateCommand(entity, entity.getLocation().getPosition().add(moveVector.getX(), moveVector.getY(), moveVector.getZ()), 0);
                                updates.add(eUp);

                                if (Settings.Debug) {
                                    Movecraft.getInstance().getLogger().info("Submitting Entity Update: " + entity.getType().getName());
                                    if (entity instanceof Item)
                                        Movecraft.getInstance().getLogger().info("Item Type: " + ((Item) entity).getItemType().getName());

                                }
                            }
                        }

                        processedEntities.set(true);
                    })
                    .submit(Movecraft.getInstance());



            synchronized (this) {
                while (!processedEntities.get()) this.wait(1);
            }

        } else {
            //add releaseTask without playermove to manager
            if (!craft.getType().getCruiseOnPilot() && craft.getState() != CraftState.SINKING)  // not necessary to release cruiseonpilot crafts, because they will already be released
                CraftManager.getInstance().addReleaseTask(craft);
        }
        //TODO: Re-add!
        //captureYield(harvestedBlocks);

        long endTime = System.currentTimeMillis();

        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info("Translation Task Took: " + (endTime - startTime) + "ms");

    }
    
    @Override
    public void postProcess() {

        Player pilot = Sponge.getServer().getPlayer(craft.getPilot()).orElse(null);
        boolean sentMapUpdate = false;

        // Check that the craft hasn't been sneakily unpiloted
        // if ( p != null ) { cruiseOnPilot crafts don't have player
        // pilots

        if (failed()) {
            // The craft translation failed
            if (pilot != null && craft.getState() != CraftState.SINKING) {
                pilot.sendMessage(Text.of(getFailMessage()));
            }

            if (isCollisionExplosion()) {
                //craft.setHitBox(getNewHitBox());
                MapUpdateManager.getInstance().scheduleUpdates(getUpdates());
                sentMapUpdate = true;
                CraftManager.getInstance().addReleaseTask(craft);

            }
        } else {
            // The craft is clear to move, perform the block updates
            MapUpdateManager.getInstance().scheduleUpdates(getUpdates());

            sentMapUpdate = true;
        }

        // only mark the craft as having finished updating if you didn't
        // send any updates to the map updater. Otherwise the map updater
        // will mark the crafts once it is done with them.
        craft.setProcessing(sentMapUpdate);
    }

    private boolean checkCraftHeight() {

        final int minY = oldHitBox.getMinY();
        final int maxY = oldHitBox.getMaxY();

        //Check if the craft is too high
        if (moveVector.getY() > -1 && craft.getType().getMaxHeightLimit() < craft.getHitBox().getMaxY()){
            moveVector = new Vector3i(moveVector.getX(), -1, moveVector.getZ());
        }else if(craft.getType().getMaxHeightAboveGround() > 0){

            final Vector3i middle = oldHitBox.getMidPoint().add(moveVector);
            int testY = minY;

            while (testY > 0){
                testY -= 1;

                if (craft.getWorld().getBlockType(middle.getX(), testY, middle.getZ()) != BlockTypes.AIR)
                    break;
            }

            if (moveVector.getY() > -1 && minY - testY > craft.getType().getMaxHeightAboveGround()) {
                moveVector = new Vector3i(moveVector.getX(), -1, moveVector.getZ());
            }
        }

        //Fail the movement if the craft is too high
        if (moveVector.getY() > 0 && maxY + moveVector.getY() > craft.getType().getMaxHeightLimit()) {
            fail("Translation Failed - Craft hit height limit.");
            return false;
        } else if (minY + moveVector.getY() < craft.getType().getMinHeightLimit() && moveVector.getY() < 0 && craft.getState() != CraftState.SINKING) {
            fail("Translation Failed - Craft hit minimum height limit.");
            return false;
        }

        return true;
    }

    private boolean craftObstructed() {
        for(Vector3i oldLocation : oldHitBox){
            final Vector3i newLocation = oldLocation.add(moveVector.getX(),moveVector.getY(),moveVector.getZ());
            //If the new location already exists in the old hitbox than this is unnecessary because a craft can't hit itself.
            if(oldHitBox.contains(newLocation)){
                newHitBox.add(newLocation);
                continue;
            }
            final BlockType testMaterial = craft.getWorld().getBlockType(newLocation);

            if ((testMaterial.equals(BlockTypes.CHEST) || testMaterial.equals(BlockTypes.TRAPPED_CHEST)) && checkChests(testMaterial, newLocation)) {
                //prevent chests collision
                fail(String.format("Translation Failed - Craft is obstructed" + " @ %d,%d,%d,%s", newLocation.getX(), newLocation.getY(), newLocation.getZ(), craft.getWorld().getBlockType(newLocation).toString()));
                return true;
            }

            boolean blockObstructed;
            if (craft.getState() == CraftState.SINKING) {
                blockObstructed = !FALL_THROUGH_BLOCKS.contains(testMaterial);
            } else {
                blockObstructed = !craft.getType().getPassthroughBlocks().contains(testMaterial) && !testMaterial.equals(BlockTypes.AIR);
            }

            boolean ignoreBlock = false;
            // air never obstructs anything (changed 4/18/2017 to prevent drilling machines)
            if (craft.getWorld().getBlockType(oldLocation).equals(BlockTypes.AIR) && blockObstructed) {
                ignoreBlock = true;
            }

            if (blockObstructed && harvestBlocks.contains(testMaterial)) {
                BlockType tmpType = craft.getWorld().getBlockType(oldLocation);
                if (harvesterBladeBlocks.contains(tmpType)) {
                    blockObstructed = false;
                    harvestedBlocks.add(newLocation);
                }
            }

            if (blockObstructed) {
                if (craft.getState() != CraftState.SINKING && craft.getType().getCollisionExplosion() == 0.0F) {
                    fail(String.format("Translation Failed - Craft is obstructed" + " @ %d,%d,%d,%s", newLocation.getX(), newLocation.getY(), newLocation.getZ(), testMaterial.getName()));
                    return true;
                }
                collisionBox.add(newLocation);
            } else {
                if (!ignoreBlock) {
                    newHitBox.add(newLocation);
                }
            } //END OF: if (blockObstructed)
        }

        return false;
    }

    private void fail(String message) {
        failed=true;
        failMessage=message;
        Player craftPilot = Sponge.getServer().getPlayer(craft.getPilot()).orElse(null);
        if (craftPilot != null) {
            craftPilot.sendMessage(Text.of(failMessage));
            Location location = craftPilot.getLocation();
            if (craft.getState() != CraftState.DISABLED) {
                craft.getWorld().playSound(SoundTypes.BLOCK_ANVIL_LAND, location.getPosition(), 1.0f, 0.25f);
                //craft.setCurTickCooldown(craft.getType().getCruiseTickCooldown());
            } else {
                craft.getWorld().playSound(SoundTypes.ENTITY_IRONGOLEM_DEATH, location.getPosition(), 5.0f, 5.0f);
                //craft.setCurTickCooldown(craft.getType().getCruiseTickCooldown());
            }
        }
    }

    private static final Vector3i[] SHIFTS = {
            new Vector3i(1,0,0),
            new Vector3i(-1,0,0),
            new Vector3i(0,0,1),
            new Vector3i(0,0,-1)};

    private boolean checkChests(BlockType mBlock, Vector3i newLoc) {
        for(Vector3i shift : SHIFTS){
            Vector3i aroundNewLoc = newLoc.add(shift);
            BlockType testMaterial = craft.getWorld().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
            if (testMaterial.equals(mBlock) && !oldHitBox.contains(aroundNewLoc)) {
                return true;
            }
        }
        return false;
    }

    //TODO: Reactivate code once possible to get a block's potential drops.
    /*
    private void captureYield(List<Vector3i> harvestedBlocks) {
        if (harvestedBlocks.isEmpty()) {
            return;
        }
        ArrayList<Inventory> chests = new ArrayList<>();
        //find chests
        for (Vector3i loc : oldHitBox) {
            BlockSnapshot block = craft.getWorld().createSnapshot(loc.getX(), loc.getY(), loc.getZ());
            block.getLocation().ifPresent(worldLocation -> {
                worldLocation.getTileEntity().ifPresent(tileEntity -> {
                    if (tileEntity.getType() == TileEntityTypes.CHEST) {
                        chests.add(((TileEntityCarrier) tileEntity).getInventory());
                    }
                });
            });
        }

        for (Vector3i harvestedBlock : harvestedBlocks) {
            BlockSnapshot block = craft.getWorld().createSnapshot(harvestedBlock.getX(), harvestedBlock.getY(), harvestedBlock.getZ());
            List<ItemStack> drops = new ArrayList<>(block.getDrops());
            //generate seed drops
            if (block.getState().getType() == BlockTypes.CROPS) {
                Random rand = new Random();
                int amount = rand.nextInt(4);
                if (amount > 0) {
                    ItemStack seeds = new ItemStack(ItemTypes.SEEDS, amount);
                    drops.add(seeds);
                }
            }
            //get contents of inventories before depositing
            block.getLocation().ifPresent(worldLocation -> {
                worldLocation.getTileEntity().ifPresent(tileEntity -> {

                    if (tileEntity instanceof TileEntityCarrier) {

                        drops.addAll(Arrays.asList(StreamSupport.stream(((TileEntityCarrier) tileEntity).getInventory().<Slot>slots().spliterator(), false).map(Slot::peek).toArray(ItemStack[]::new)));

                    }
                });
            });

            for (ItemStack drop : drops) {
                ItemStack retStack = putInToChests(drop, chests);
                if (retStack != null)
                    //drop items on position
                    updates.add(new ItemDropUpdateCommand(new Location<>(craft.getWorld(), harvestedBlock.getX(), harvestedBlock.getY(), harvestedBlock.getZ()), retStack));
            }
        }
    }


    private ItemStack putInToChests(ItemStack stack, ArrayList<Inventory> inventories) {
        if (stack == null)
            return null;
        if (inventories == null || inventories.isEmpty())
            return stack;
        for (Inventory inv : inventories) {

            inv.offer(stack);

            if (stack.getQuantity() == 0) {
                return null;
            }

        }
        return stack;
    }*/

    public boolean failed(){
        return failed;
    }

    public String getFailMessage() {
        return failMessage;
    }

    public HashHitBox getNewHitBox() {
        return newHitBox;
    }

    public Collection<UpdateCommand> getUpdates() {
        return updates;
    }

    public com.flowpowered.math.vector.Vector3i getMoveVector() {
        return moveVector;
    }

    public boolean isCollisionExplosion() {
        return collisionExplosion;
    }
}
