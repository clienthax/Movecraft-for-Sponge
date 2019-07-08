package io.github.pulverizer.movecraft.async.translation;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableSet;
import io.github.pulverizer.movecraft.CraftState;
import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.async.AsyncTask;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.CraftTranslateEvent;
import io.github.pulverizer.movecraft.mapUpdater.update.*;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import io.github.pulverizer.movecraft.utils.HitBox;
import io.github.pulverizer.movecraft.utils.MutableHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.carrier.Furnace;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TranslationTask extends AsyncTask {
    private static final ImmutableSet<BlockType> FALL_THROUGH_BLOCKS = ImmutableSet.of(BlockTypes.AIR, BlockTypes.FLOWING_WATER, BlockTypes.FLOWING_LAVA, BlockTypes.TALLGRASS, BlockTypes.YELLOW_FLOWER, BlockTypes.RED_FLOWER, BlockTypes.BROWN_MUSHROOM, BlockTypes.RED_MUSHROOM, BlockTypes.TORCH, BlockTypes.FIRE, BlockTypes.REDSTONE_WIRE, BlockTypes.WHEAT, BlockTypes.STANDING_SIGN, BlockTypes.LADDER, BlockTypes.WALL_SIGN, BlockTypes.LEVER, BlockTypes.LIGHT_WEIGHTED_PRESSURE_PLATE, BlockTypes.HEAVY_WEIGHTED_PRESSURE_PLATE, BlockTypes.STONE_PRESSURE_PLATE, BlockTypes.WOODEN_PRESSURE_PLATE, BlockTypes.UNLIT_REDSTONE_TORCH, BlockTypes.REDSTONE_TORCH, BlockTypes.STONE_BUTTON, BlockTypes.SNOW_LAYER, BlockTypes.REEDS, BlockTypes.FENCE, BlockTypes.ACACIA_FENCE, BlockTypes.BIRCH_FENCE, BlockTypes.DARK_OAK_FENCE, BlockTypes.JUNGLE_FENCE, BlockTypes.NETHER_BRICK_FENCE, BlockTypes.SPRUCE_FENCE, BlockTypes.UNPOWERED_REPEATER, BlockTypes.POWERED_REPEATER, BlockTypes.WATERLILY, BlockTypes.CARROTS, BlockTypes.POTATOES, BlockTypes.WOODEN_BUTTON, BlockTypes.CARPET);

    private int dx, dy, dz;
    private HashHitBox newHitBox, oldHitBox;
    private boolean failed;
    private boolean collisionExplosion = false;
    private String failMessage;
    private Collection<UpdateCommand> updates = new HashSet<>();
    private boolean taskFinished = false;

    public TranslationTask(Craft c, Vector3i moveVector) {
        super(c);
        this.dx = moveVector.getX();
        this.dy = moveVector.getY();
        this.dz = moveVector.getZ();
        newHitBox = new HashHitBox();
        oldHitBox = new HashHitBox(c.getHitBox());
    }

    @Override
    protected void execute() {

        //Check if there is anything to move
        if(oldHitBox.isEmpty()){
            return;
        }
        if (getCraft().getState() == CraftState.DISABLED) {
            fail("Craft is disabled!");
            return;
        }
        final int minY = oldHitBox.getMinY();
        final int maxY = oldHitBox.getMaxY();

        //Check if the craft is too high
        if(craft.getType().getMaxHeightLimit() < craft.getHitBox().getMinY()){
            dy-=1;
        }else if(craft.getType().getMaxHeightAboveGround() > 0){
            final MovecraftLocation middle = oldHitBox.getMidPoint();
            int testY = minY;
            while (testY > 0){
                testY -= 1;
                if (craft.getWorld().getBlockType(middle.getX(),testY,middle.getZ()) != BlockTypes.AIR)
                    break;
            }
            if (minY - testY > craft.getType().getMaxHeightAboveGround()) {
                dy -= 1;
            }
        }

        //Fail the movement if the craft is too high
        if (dy>0 && maxY + dy > craft.getType().getMaxHeightLimit()) {
            fail("Translation Failed - Craft hit height limit.");
            return;
        } else if (minY + dy < craft.getType().getMinHeightLimit() && dy < 0 && craft.getState() != CraftState.SINKING) {
            fail("Translation Failed - Craft hit minimum height limit.");
            return;
        }

        // check for fuel, burn some from a furnace if needed. Blocks of coal are supported, in addition to coal and charcoal
        double fuelBurnRate = getCraft().getType().getFuelBurnRate();
        if (fuelBurnRate != 0.0 && getCraft().getState() != CraftState.SINKING) {

            boolean fuelBurned = getCraft().burnFuel(fuelBurnRate);

            if (!fuelBurned) {
                failed = true;
                failMessage = "Translation Failed - Craft out of fuel";
            }
        }

        final List<BlockType> harvestBlocks = craft.getType().getHarvestBlocks();
        final List<Location<World>> harvestedBlocks = new ArrayList<>();
        final List<BlockType> harvesterBladeBlocks = craft.getType().getHarvesterBladeBlocks();
        final HashHitBox collisionBox = new HashHitBox();
        for(MovecraftLocation oldLocation : oldHitBox){
            final MovecraftLocation newLocation = oldLocation.translate(dx,dy,dz);
            //If the new location already exists in the old hitbox than this is unnecessary because a craft can't hit
            //itself
            if(oldHitBox.contains(newLocation)){
                newHitBox.add(newLocation);
                continue;
            }
            final BlockType testMaterial = newLocation.toSponge(craft.getWorld()).getBlockType();

            if ((testMaterial.equals(BlockTypes.CHEST) || testMaterial.equals(BlockTypes.TRAPPED_CHEST)) && checkChests(testMaterial, newLocation)) {
                //prevent chests collision
                fail(String.format("Translation Failed - Craft is obstructed" + " @ %d,%d,%d,%s", newLocation.getX(), newLocation.getY(), newLocation.getZ(), newLocation.toSponge(craft.getWorld()).getBlock().getType().toString()));
                return;
            }

            boolean blockObstructed;
            if (craft.getState() == CraftState.SINKING) {
                blockObstructed = !FALL_THROUGH_BLOCKS.contains(testMaterial);
            } else {
                blockObstructed = !craft.getType().getPassthroughBlocks().contains(testMaterial) && !testMaterial.equals(BlockTypes.AIR);
            }

            boolean ignoreBlock = false;
            // air never obstructs anything (changed 4/18/2017 to prevent drilling machines)
            if (oldLocation.toSponge(craft.getWorld()).getBlock().getType().equals(BlockTypes.AIR) && blockObstructed) {
                ignoreBlock = true;
            }

            if (blockObstructed && !harvestBlocks.isEmpty() && harvestBlocks.contains(testMaterial)) {
                BlockType tmpType = oldLocation.toSponge(craft.getWorld()).getBlockType();
                if (harvesterBladeBlocks.size() > 0 && harvesterBladeBlocks.contains(tmpType)) {
                    blockObstructed = false;
                    harvestedBlocks.add(newLocation.toSponge(craft.getWorld()));
                }
            }

            if (blockObstructed) {
                if (craft.getState() != CraftState.SINKING && craft.getType().getCollisionExplosion() == 0.0F) {
                    fail(String.format("Translation Failed - Craft is obstructed" + " @ %d,%d,%d,%s", newLocation.getX(), newLocation.getY(), newLocation.getZ(), testMaterial.toString()));
                    return;
                }
                collisionBox.add(newLocation);
            } else {
                if (!ignoreBlock) {
                    newHitBox.add(newLocation);
                }
            } //END OF: if (blockObstructed)
        }

        //call event
        CraftTranslateEvent event = new CraftTranslateEvent(craft, oldHitBox, newHitBox);
        Sponge.getEventManager().post(event);
        if(event.isCancelled()){
            this.fail(event.getFailMessage());
            return;
        }

        if(craft.getState() == CraftState.SINKING){
            for(MovecraftLocation location : collisionBox){
                if (craft.getType().getExplodeOnCrash() > 0.0F) {
                    if (System.currentTimeMillis() - craft.getOriginalPilotTime() <= 1000) {
                        continue;
                    }
                    Location<World> loc = location.toSponge(craft.getWorld());
                    if (!loc.getBlock().getType().equals(BlockTypes.AIR)  && ThreadLocalRandom.current().nextDouble(1) < .05) {
                        updates.add(new ExplosionUpdateCommand( loc, craft.getType().getExplodeOnCrash()));
                        collisionExplosion=true;
                    }
                }
                List<MovecraftLocation> toRemove = new ArrayList<>();
                MovecraftLocation next = location;
                do {
                    toRemove.add(next);
                    next = next.add(new MovecraftLocation(0,1,0));
                }while (newHitBox.contains(next));

                newHitBox.removeAll(toRemove);
            }

        }else{
            for(MovecraftLocation location : collisionBox){
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
                Location<World> loc = location.toSponge(craft.getWorld());
                if (!loc.getBlock().getType().equals(BlockTypes.AIR)) {
                    updates.add(new ExplosionUpdateCommand(loc, explosionKey));
                    collisionExplosion = true;
                }
                if (craft.getType().getFocusedExplosion()) { // don't handle any further collisions if it is set to focusedexplosion
                    break;
                }
            }
        }

        if(!collisionBox.isEmpty() && craft.getType().getCruiseOnPilot()){
            CraftManager.getInstance().removeCraft(craft);
            for(MovecraftLocation location : oldHitBox){
                updates.add(new BlockCreateCommand(craft.getWorld(), location, BlockTypes.AIR));
            }
            newHitBox = new HashHitBox();
        }

        updates.add(new CraftTranslateCommand(craft, new MovecraftLocation(dx, dy, dz), getNewHitBox()));

        //prevents torpedo and rocket pilots
        if (craft.getType().getMoveEntities() && craft.getState() != CraftState.SINKING) {

            if (Settings.Debug)
                Movecraft.getInstance().getLogger().info("Craft moves Entities.");

            Task.builder()
                    .execute(() -> {

                        Movecraft.getInstance().getLogger().info("Searching for Entities on Craft.");

                        for(Entity entity : craft.getWorld().getIntersectingEntities(new AABB(oldHitBox.getMinX() - 0.5, oldHitBox.getMinY() - 0.5, oldHitBox.getMinZ() - 0.5, oldHitBox.getMaxX() + 1.5, oldHitBox.getMaxY() + 1.5, oldHitBox.getMaxZ()+1.5))){

                            if (entity.getType() == EntityTypes.PLAYER || entity.getType() == EntityTypes.PRIMED_TNT || !craft.getType().getOnlyMovePlayers()) {
                                if (Settings.Debug) {
                                    Movecraft.getInstance().getLogger().info("Registering Entity of type " + entity.getType().getName() + " for movement.");
                                }
                                EntityUpdateCommand eUp = new EntityUpdateCommand(entity, entity.getLocation().getPosition().add(dx, dy, dz), 0);
                                updates.add(eUp);
                            }
                        }

                        if (Settings.Debug)
                            Movecraft.getInstance().getLogger().info("Submitting Entity Movements.");

                        setTaskFinished();
                    })
                    .submit(Movecraft.getInstance());

            while (!taskFinished) {
                if (Settings.Debug)
                    Movecraft.getInstance().getLogger().info("Still Processing Entities!");
            }

            if (taskFinished && Settings.Debug)
                Movecraft.getInstance().getLogger().info("Processed Entities.");

        } else {
            //add releaseTask without playermove to manager
            if (!craft.getType().getCruiseOnPilot() && craft.getState() != CraftState.SINKING)  // not necessary to release cruiseonpilot crafts, because they will already be released
                CraftManager.getInstance().addReleaseTask(craft);
        }
        //TODO: Re-add!
        //captureYield(harvestedBlocks);

    }

    public void setTaskFinished(){
        taskFinished = true;
    }

    private static HitBox translateHitBox(HitBox hitBox, MovecraftLocation shift){
        MutableHitBox output = new HashHitBox();
        for(MovecraftLocation location : hitBox){
            output.add(location.add(shift));
        }
        return output;
    }

    private void fail(String message) {
        failed=true;
        failMessage=message;
        Player craftPilot = Sponge.getServer().getPlayer(craft.getPilot()).orElse(null);
        if (craftPilot != null) {
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

    private static final MovecraftLocation[] SHIFTS = {
            new MovecraftLocation(1,0,0),
            new MovecraftLocation(-1,0,0),
            new MovecraftLocation(0,0,1),
            new MovecraftLocation(0,0,-1)};

    private boolean checkChests(BlockType mBlock, MovecraftLocation newLoc) {
        for(MovecraftLocation shift : SHIFTS){
            MovecraftLocation aroundNewLoc = newLoc.add(shift);
            BlockType testMaterial = craft.getWorld().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
            if (testMaterial.equals(mBlock) && !oldHitBox.contains(aroundNewLoc)) {
                return true;
            }
        }
        return false;
    }

    //TODO: Reactivate code once possible to get a block's potential drops.
    /*
    private void captureYield(List<MovecraftLocation> harvestedBlocks) {
        if (harvestedBlocks.isEmpty()) {
            return;
        }
        ArrayList<Inventory> chests = new ArrayList<>();
        //find chests
        for (MovecraftLocation loc : oldHitBox) {
            BlockSnapshot block = craft.getWorld().createSnapshot(loc.getX(), loc.getY(), loc.getZ());
            block.getLocation().ifPresent(worldLocation -> {
                worldLocation.getTileEntity().ifPresent(tileEntity -> {
                    if (tileEntity.getType() == TileEntityTypes.CHEST) {
                        chests.add(((TileEntityCarrier) tileEntity).getInventory());
                    }
                });
            });
        }

        for (MovecraftLocation harvestedBlock : harvestedBlocks) {
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
    */

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
    }

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

    public int getDx(){
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public int getDz() {
        return dz;
    }

    public boolean isCollisionExplosion() {
        return collisionExplosion;
    }
}
