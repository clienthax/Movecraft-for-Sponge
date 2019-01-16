package io.github.pulverizer.movecraft.async.translation;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.MovecraftLocation;
import io.github.pulverizer.movecraft.async.AsyncTask;
import io.github.pulverizer.movecraft.craft.Craft;
import io.github.pulverizer.movecraft.craft.CraftManager;
import io.github.pulverizer.movecraft.events.CraftTranslateEvent;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import io.github.pulverizer.movecraft.mapUpdater.update.*;
import io.github.pulverizer.movecraft.utils.HashHitBox;
import io.github.pulverizer.movecraft.utils.HitBox;
import io.github.pulverizer.movecraft.utils.MutableHitBox;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.TileEntityType;
import org.spongepowered.api.block.tileentity.TileEntityTypes;
import org.spongepowered.api.block.tileentity.carrier.Chest;
import org.spongepowered.api.block.tileentity.carrier.Furnace;
import org.spongepowered.api.block.tileentity.carrier.TileEntityCarrier;
import org.spongepowered.api.data.Property;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.query.QueryOperationType;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.world.Location;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.StreamSupport;

public class TranslationTask extends AsyncTask {
    private static final BlockType[] FALL_THROUGH_BLOCKS = {BlockTypes.AIR, 8, 9, 10, 11, 31, 37, 38, 39, 40, 50, 51, 55, 59, 63, 65, 68, 69, 70, 72, 75, 76, 77, 78, 83, 85, 93, 94, 111, 141, 142, 143, 171};

    private int dx, dy, dz;
    private HashHitBox newHitBox, oldHitBox;
    private boolean failed;
    private boolean collisionExplosion = false;
    private String failMessage;
    private Collection<UpdateCommand> updates = new HashSet<>();

    public TranslationTask(Craft c, int dx, int dy, int dz) {
        super(c);
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        newHitBox = new HashHitBox();
        oldHitBox = new HashHitBox(c.getHitBox());
    }

    @Override
    protected void excecute() {

        //Check if theres anything to move
        if(oldHitBox.isEmpty()){
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
                if (craft.getW().getBlockType(middle.getX(),testY,middle.getZ()) != BlockTypes.AIR)
                    break;
            }
            if (minY - testY > craft.getType().getMaxHeightAboveGround()) {
                dy -= 1;
            }
        }

        //Fail the movement if the craft is too high
        if (dy>0 && maxY + dy > craft.getType().getMaxHeightLimit()) {
            fail(I18nSupport.getInternationalisedString("Translation - Failed Craft hit height limit"));
            return;
        } else if (minY + dy < craft.getType().getMinHeightLimit() && dy < 0 && !craft.getSinking()) {
            fail(I18nSupport.getInternationalisedString("Translation - Failed Craft hit minimum height limit"));
            return;
        }

        //TODO: Check fuel
        if (!checkFuel()) {
            fail(I18nSupport.getInternationalisedString("Translation - Failed Craft out of fuel"));
            return;
        }

        final List<BlockType> harvestBlocks = craft.getType().getHarvestBlocks();
        final List<MovecraftLocation> harvestedBlocks = new ArrayList<>();
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
            final BlockType testMaterial = newLocation.toSponge(craft.getW()).getBlockType();

            if ((testMaterial.equals(BlockTypes.CHEST) || testMaterial.equals(BlockTypes.TRAPPED_CHEST)) && checkChests(testMaterial, newLocation)) {
                //prevent chests collision
                fail(String.format(I18nSupport.getInternationalisedString("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s", newLocation.getX(), newLocation.getY(), newLocation.getZ(), newLocation.toSponge(craft.getW()).getBlock().getType().toString()));
                return;
            }

            boolean blockObstructed;
            if (craft.getSinking()) {
                blockObstructed = !(Arrays.binarySearch(FALL_THROUGH_BLOCKS, testMaterial.getId()) >= 0);
            } else {
                blockObstructed = !craft.getType().getPassthroughBlocks().contains(testMaterial) && !testMaterial.equals(BlockTypes.AIR);
            }

            boolean ignoreBlock = false;
            // air never obstructs anything (changed 4/18/2017 to prevent drilling machines)
            if (oldLocation.toSponge(craft.getW()).getBlock().getType().equals(BlockTypes.AIR) && blockObstructed) {
                ignoreBlock = true;
            }

            if (blockObstructed && !harvestBlocks.isEmpty() && harvestBlocks.contains(testMaterial)) {
                BlockType tmpType = oldLocation.toSponge(craft.getW()).getBlockType();
                if (harvesterBladeBlocks.size() > 0 && harvesterBladeBlocks.contains(tmpType)) {
                    blockObstructed = false;
                    harvestedBlocks.add(newLocation);
                }
            }

            if (blockObstructed) {
                if (!craft.getSinking() && craft.getType().getCollisionExplosion() == 0.0F) {
                    fail(String.format(I18nSupport.getInternationalisedString("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s", newLocation.getX(), newLocation.getY(), newLocation.getZ(), testMaterial.toString()));
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

        if(craft.getSinking()){
            for(MovecraftLocation location : collisionBox){
                if (craft.getType().getExplodeOnCrash() > 0.0F) {
                    if (System.currentTimeMillis() - craft.getOrigPilotTime() <= 1000) {
                        continue;
                    }
                    Location loc = location.toSponge(craft.getW());
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
                if (!(craft.getType().getCollisionExplosion() != 0.0F) || System.currentTimeMillis() - craft.getOrigPilotTime() <= 1000) {
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
                Location loc = location.toSponge(craft.getW());
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
                updates.add(new BlockCreateCommand(craft.getW(), location, BlockTypes.AIR));
            }
            newHitBox = new HashHitBox();
        }

        updates.add(new CraftTranslateCommand(craft, new MovecraftLocation(dx, dy, dz)));

        //prevents torpedo and rocket pilots
        if (craft.getType().getMoveEntities() && !(craft.getSinking() && craft.getType().getOnlyMovePlayers())) {

            Task.builder()
                    .name("TranslationTask:220")
                    .execute(() -> {
                        HashHitBox craftHitBox = craft.getHitBox();
                        for(Entity entity : craft.getW().getIntersectingEntities(new AABB(craftHitBox.getMinX(), craftHitBox.getMinY(), craftHitBox.getMinZ(), craftHitBox.getMaxX(), craftHitBox.getMaxY(), craftHitBox.getMaxZ()))){
                            if (entity.getType() == EntityTypes.PLAYER && !craft.getSinking()) {
                                Player player = (Player) entity;
                                craft.getMovedPlayers().put(player, System.currentTimeMillis());
                                EntityUpdateCommand eUp = new EntityUpdateCommand(entity, dx, dy, dz, 0, 0);
                                updates.add(eUp);
                            } else if (!craft.getType().getOnlyMovePlayers() || entity.getType() == EntityTypes.PRIMED_TNT) {
                                EntityUpdateCommand eUp = new EntityUpdateCommand(entity, dx, dy, dz, 0, 0);
                                updates.add(eUp);
                            }
                        }
                    })
                    .submit(Movecraft.getInstance());
        } else {
            //add releaseTask without playermove to manager
            if (!craft.getType().getCruiseOnPilot() && !craft.getSinking())  // not necessary to release cruiseonpilot crafts, because they will already be released
                CraftManager.getInstance().addReleaseTask(craft);
        }
        captureYield(harvestedBlocks);

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
        Player craftPilot = CraftManager.getInstance().getPlayerFromCraft(craft);
        if (craftPilot != null) {
            Location location = craftPilot.getLocation();
            if (!craft.getDisabled()) {
                craft.getW().playSound(SoundTypes.BLOCK_ANVIL_LAND, location.getPosition(), 1.0f, 0.25f);
                //craft.setCurTickCooldown(craft.getType().getCruiseTickCooldown());
            } else {
                craft.getW().playSound(SoundTypes.ENTITY_IRONGOLEM_DEATH, location.getPosition(), 5.0f, 5.0f);
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
            BlockType testMaterial = craft.getW().getBlockType(aroundNewLoc.getX(), aroundNewLoc.getY(), aroundNewLoc.getZ());
            if (testMaterial.equals(mBlock) && !oldHitBox.contains(aroundNewLoc)) {
                return true;
            }
        }
        return false;
    }

    private void captureYield(List<MovecraftLocation> harvestedBlocks) {
        if (harvestedBlocks.isEmpty()) {
            return;
        }
        ArrayList<Inventory> chests = new ArrayList<>();
        //find chests
        for (MovecraftLocation loc : oldHitBox) {
            BlockSnapshot block = craft.getW().createSnapshot(loc.getX(), loc.getY(), loc.getZ());
            block.getLocation().ifPresent(worldLocation -> {
                worldLocation.getTileEntity().ifPresent(tileEntity -> {
                    if (tileEntity.getType() == TileEntityTypes.CHEST) {
                        chests.add(((TileEntityCarrier) tileEntity).getInventory());
                    }
                });
            });
        }

        for (MovecraftLocation harvestedBlock : harvestedBlocks) {
            BlockSnapshot block = craft.getW().createSnapshot(harvestedBlock.getX(), harvestedBlock.getY(), harvestedBlock.getZ());
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
                    updates.add(new ItemDropUpdateCommand(new Location<>(craft.getW(), harvestedBlock.getX(), harvestedBlock.getY(), harvestedBlock.getZ()), retStack));
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
    }

    private boolean checkFuel(){
        // check for fuel, burn some from a furnace if needed. Blocks of coal are supported, in addition to coal and charcoal
        double fuelBurnRate = craft.getType().getFuelBurnRate();
        // going down doesn't require fuel
        if (dy == -1 && dx == 0 && dz == 0)
            fuelBurnRate = 0.0;

        if (fuelBurnRate == 0.0 || craft.getSinking()) {
            return true;
        }
        if (craft.getBurningFuel() >= fuelBurnRate) {
            craft.setBurningFuel(craft.getBurningFuel() - fuelBurnRate);
            return true;
        }

        Furnace fuelHolder = null;
        for (MovecraftLocation bTest : oldHitBox) {
            BlockSnapshot b = getCraft().getW().createSnapshot(bTest.getX(), bTest.getY(), bTest.getZ());
            if (b.getState().getType() == BlockTypes.FURNACE || b.getState().getType() == BlockTypes.LIT_FURNACE) {
                fuelHolder = b.getLocation()
                        .flatMap(Location::getTileEntity)
                        .filter(Furnace.class::isInstance)
                        .map(Furnace.class::cast)
                        .filter(furnace -> furnace.getInventory().contains(ItemTypes.COAL) || furnace.getInventory().contains(ItemTypes.COAL_BLOCK))
                        .get();
            }
        }

        if (fuelHolder == null) {
            fail(I18nSupport.getInternationalisedString("Translation - Failed Craft out of fuel"));
            return false;
        }


        Inventory inventory = fuelHolder.getInventory();

        if (inventory.contains(ItemTypes.COAL)) {
            inventory.query(QueryOperationTypes.ITEM_TYPE.of(ItemTypes.COAL)).poll(1);
            getCraft().setBurningFuel(getCraft().getBurningFuel() + 7.0);
        } else {
            inventory.query(QueryOperationTypes.ITEM_TYPE.of(ItemTypes.COAL_BLOCK)).poll(1);
            getCraft().setBurningFuel(getCraft().getBurningFuel() + 79.0);

        }

        return true;
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
