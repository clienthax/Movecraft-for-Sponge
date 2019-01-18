package io.github.pulverizer.movecraft;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.craft.Craft;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.Location;
import io.github.pulverizer.movecraft.config.Settings;
import io.github.pulverizer.movecraft.utils.MathUtils;
import io.github.pulverizer.movecraft.utils.CollectionUtils;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.block.CraftBlockState;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers;
import org.spongepowered.api.world.World;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class WorldHandler {

    private static final EnumBlockRotation ROTATION[];
    static {
        ROTATION = new EnumBlockRotation[3];
        ROTATION[Rotation.NONE.ordinal()] = EnumBlockRotation.NONE;
        ROTATION[Rotation.CLOCKWISE.ordinal()] = EnumBlockRotation.CLOCKWISE_90;
        ROTATION[Rotation.ANTICLOCKWISE.ordinal()] = EnumBlockRotation.COUNTERCLOCKWISE_90;
    }
    private final NextTickProvider tickProvider = new NextTickProvider();
    private final HashMap<World,List<TileEntity>> bMap = new HashMap<>();
    private MethodHandle internalTeleportMH;

    public WorldHandler() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method teleport = null;
        try {
            teleport = PlayerConnection.class.getDeclaredMethod("internalTeleport", double.class, double.class, double.class, float.class, float.class, Set.class);
            teleport.setAccessible(true);
            internalTeleportMH = lookup.unreflect(teleport);

        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void addPlayerLocation(Player player, double x, double y, double z, float yaw, float pitch){
        if(internalTeleportMH == null) {
            //something went wrong
            Location<World> playerLoc = player.getLocation();
            player.setLocationAndRotation(new Location<>(player.getWorld(), x + playerLoc.getX(),y + playerLoc.getY(),z + playerLoc.getZ()), player.getRotation().add(pitch, yaw, 0));
            return;
        }
        try {
            internalTeleportMH.invoke(player.getConnection(), x, y, z, yaw, pitch, EnumSet.allOf(PacketPlayOutPosition.EnumPlayerTeleportFlags.class));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public void rotateCraft(Craft craft, MovecraftLocation originPoint, Rotation rotation) {
        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        HashMap<Vector3i,Vector3i> rotatedBlockPositions = new HashMap<>();
        Rotation counterRotation = rotation == Rotation.CLOCKWISE ? Rotation.ANTICLOCKWISE : Rotation.CLOCKWISE;
        for(MovecraftLocation newLocation : craft.getHitBox()){
            rotatedBlockPositions.put(locationToBlockPosition(MathUtils.rotateVec(counterRotation, newLocation.subtract(originPoint)).add(originPoint)), locationToBlockPosition(newLocation));
        }
        //*******************************************
        //*         Step two: Get the tiles         *
        //*******************************************
        World nativeWorld = craft.getW();
        List<TileHolder> tiles = new ArrayList<>();
        //get the tiles
        for(Vector3i blockPosition : rotatedBlockPositions.keySet()){
            //TileEntity tile = nativeWorld.removeTileEntity(blockPosition);
            TileEntity tile = removeTileEntity(nativeWorld,blockPosition);
            if(tile == null)
                continue;
            tile.a(ROTATION[rotation.ordinal()]);
            //get the nextTick to move with the tile
            tiles.add(new TileHolder(tile, tickProvider.getNextTick(nativeWorld,blockPosition), blockPosition));
        }

        //*******************************************
        //*   Step three: Translate all the blocks  *
        //*******************************************
        // blockedByWater=false means an ocean-going vessel
        //TODO: Simplify
        //TODO: go by chunks
        //TODO: Don't move unnecessary blocks
        //get the blocks and rotate them
        HashMap<Vector3i,IBlockData> blockData = new HashMap<>();
        for(Vector3i blockPosition : rotatedBlockPositions.keySet()){
            blockData.put(blockPosition,nativeWorld.getBlockType(blockPosition).a(ROTATION[rotation.ordinal()]));
        }
        //create the new block
        for(Map.Entry<Vector3i,IBlockData> entry : blockData.entrySet()) {
            setBlockFast(nativeWorld, rotatedBlockPositions.get(entry.getKey()), entry.getValue());
        }


        //*******************************************
        //*    Step four: replace all the tiles     *
        //*******************************************
        //TODO: go by chunks
        for(TileHolder tileHolder : tiles){
            moveTileEntity(nativeWorld, rotatedBlockPositions.get(tileHolder.getTilePosition()),tileHolder.getTile());
            if(tileHolder.getNextTick()==null)
                continue;
            final long currentTime = nativeWorld.worldData.getTime();
            nativeWorld.b(rotatedBlockPositions.get(tileHolder.getNextTick().a), tileHolder.getNextTick().a(), (int) (tileHolder.getNextTick().b - currentTime), tileHolder.getNextTick().c);
        }

        //*******************************************
        //*   Step five: Destroy the leftovers      *
        //*******************************************
        //TODO: add support for pass-through
        Collection<Vector3i> deleteBlockPositions =  CollectionUtils.filter(rotatedBlockPositions.keySet(),rotatedBlockPositions.values());
        for(Vector3i blockPosition : deleteBlockPositions){
            setBlockFast(nativeWorld, blockPosition, BlockTypes.AIR.getDefaultState());
        }

        //*******************************************
        //*       Step six: Update the blocks       *
        //*******************************************
        for(Vector3i newBlockPosition : rotatedBlockPositions.values()) {
            CraftBlockState.getBlockState(nativeWorld,newBlockPosition.getX(), newBlockPosition.getY(), newBlockPosition.getZ()).update(false,false);
        }
        for(Vector3i deletedBlockPosition : deleteBlockPositions){
            CraftBlockState.getBlockState(nativeWorld,deletedBlockPosition.getX(), deletedBlockPosition.getY(), deletedBlockPosition.getZ()).update(false,false);
        }
        //*******************************************
        //*       Step seven: Send to players       *
        //*******************************************
        List<Chunk> chunks = new ArrayList<>();
        for(Vector3i blockPosition : rotatedBlockPositions.values()){
            Chunk chunk = nativeWorld.getChunkAtBlock(blockPosition);
            if(!chunks.contains(chunk)){
                chunks.add(chunk);
            }
        }
        for(Vector3i blockPosition : deleteBlockPositions){
            Chunk chunk = nativeWorld.getChunkAtBlock(blockPosition);
            if(!chunks.contains(chunk)){
                chunks.add(chunk);
            }
        }
    }

    public void translateCraft(Craft craft, MovecraftLocation displacement) {
        //TODO: Add support for rotations
        //A craftTranslateCommand should only occur if the craft is moving to a valid position
        //*******************************************
        //*      Step one: Convert to Positions     *
        //*******************************************
        Vector3i translateBlockVector = locationToBlockPosition(displacement);
        List<Vector3i> blockPositions = new ArrayList<>();
        for(MovecraftLocation movecraftLocation : craft.getHitBox()) {
            blockPositions.add(locationToBlockPosition((movecraftLocation)).b(translateBlockVector));

        }
        //*******************************************
        //*         Step two: Get the tiles         *
        //*******************************************
        World nativeWorld = craft.getW();
        List<TileHolder> tiles = new ArrayList<>();
        //get the tiles
        for(Vector3i blockPosition : blockPositions){
            if(nativeWorld.getBlock(blockPosition) == BlockTypes.AIR.getDefaultState())
                continue;
            //TileEntity tile = nativeWorld.removeTileEntity(blockPosition);
            TileEntity tile = removeTileEntity(nativeWorld,blockPosition);
            if(tile == null)
                continue;
            //get the nextTick to move with the tile

            //nativeWorld.capturedTileEntities.remove(blockPosition);
            //nativeWorld.getChunkAtWorldCoords(blockPosition).getTileEntities().remove(blockPosition);
            tiles.add(new TileHolder(tile, tickProvider.getNextTick(nativeWorld,blockPosition), blockPosition));

        }
        //*******************************************
        //*   Step three: Translate all the blocks  *
        //*******************************************
        // blockedByWater=false means an ocean-going vessel
        //TODO: Simplify
        //TODO: go by chunks
        //TODO: Don't move unnecessary blocks
        //get the blocks
        List<IBlockData> blockData = new ArrayList<>();
        for(Vector3i blockPosition : blockPositions){
            blockData.add(nativeWorld.getType(blockPosition));
        }
        //translate the blockPositions
        List<Vector3i> newBlockPositions = new ArrayList<>();
        for(Vector3i blockPosition : blockPositions){
            newBlockPositions.add(blockPosition.a(translateBlockVector));
        }
        //create the new block
        for(int i = 0; i<newBlockPositions.size(); i++) {
            setBlockFast(nativeWorld, newBlockPositions.get(i), blockData.get(i));
        }
        //*******************************************
        //*    Step four: replace all the tiles     *
        //*******************************************
        //TODO: go by chunks
        for(TileHolder tileHolder : tiles){
            moveTileEntity(nativeWorld, tileHolder.getTilePosition().a(translateBlockVector),tileHolder.getTile());
            if(tileHolder.getNextTick()==null)
                continue;
            final long currentTime = nativeWorld.worldData.getTime();
            nativeWorld.b(tileHolder.getNextTick().a.a(translateBlockVector), tileHolder.getNextTick().a(), (int) (tileHolder.getNextTick().b - currentTime), tileHolder.getNextTick().c);
        }
        //*******************************************
        //*   Step five: Destroy the leftovers      *
        //*******************************************
        Collection<Vector3i> deleteBlockPositions =  CollectionUtils.filter(blockPositions,newBlockPositions);
        for(Vector3i blockPosition : deleteBlockPositions){
            setBlockFast(nativeWorld, blockPosition, BlockTypes.AIR.getDefaultState());
        }

        //*******************************************
        //*       Step six: Update the blocks       *
        //*******************************************
        for(Vector3i newBlockPosition : newBlockPositions) {
            CraftBlockState.getBlockState(nativeWorld,newBlockPosition.getX(), newBlockPosition.getY(), newBlockPosition.getZ()).update(false,false);
        }
        for(Vector3i deletedBlockPosition : deleteBlockPositions){
            CraftBlockState.getBlockState(nativeWorld,deletedBlockPosition.getX(), deletedBlockPosition.getY(), deletedBlockPosition.getZ()).update(false,false);
        }
        //*******************************************
        //*       Step seven: Send to players       *
        //*******************************************
        List<Chunk> chunks = new ArrayList<>();
        for(Vector3i blockPosition : newBlockPositions){
            Chunk chunk = nativeWorld.getChunkAtWorldCoords(blockPosition);
            if(!chunks.contains(chunk)){
                chunks.add(chunk);
            }
        }
        for(Vector3i blockPosition : deleteBlockPositions){
            Chunk chunk = nativeWorld.getChunkAtWorldCoords(blockPosition);
            if(!chunks.contains(chunk)){
                chunks.add(chunk);
            }
        }
        //sendToPlayers(chunks.toArray(new Chunk[0]));
    }

    private TileEntity removeTileEntity(World world, Vector3i blockPosition){
        TileEntity tile = world.getTileEntity(blockPosition);
        if(tile == null)
            return null;
        //cleanup
        world.capturedTileEntities.remove(blockPosition);
        world.getChunkAtWorldCoords(blockPosition).getTileEntities().remove(blockPosition);
        if(!Settings.IsPaper)
            world.tileEntityList.remove(tile);
        world.tileEntityListTick.remove(tile);
        if(!bMap.containsKey(world)){
            try {
                Field bField = World.class.getDeclaredField("b");
                bField.setAccessible(true);
                bMap.put(world, (List<TileEntity>) bField.get(world));
            } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e1) {
                e1.printStackTrace();
            }
        }
        bMap.get(world).remove(tile);
        return tile;
    }

    private Vector3i locationToBlockPosition(MovecraftLocation loc) {
        return new Vector3i(loc.getX(), loc.getY(), loc.getZ());
    }

    private void setBlockFast(World world, Vector3i blockPosition,IBlockData data) {
        Chunk chunk = world.getChunkAtBlock(blockPosition);
        ChunkSection chunkSection = chunk.getSections()[blockPosition.getY()>>4];
        if (chunkSection == null) {
            // Put a GLASS block to initialize the section. It will be replaced next with the real block.
            chunk.a(blockPosition, BlockTypes.GLASS.getDefaultState());
            chunkSection = chunk.getSections()[blockPosition.getY() >> 4];
        }

        chunkSection.setType(blockPosition.getX()&15, blockPosition.getY()&15, blockPosition.getZ()&15, data);
    }

    public void setBlockFast(Location<World> location, BlockType blockType, byte data){
        setBlockFast(location, Rotation.NONE, blockType, data);
    }

    public void setBlockFast(Location<World> location, Rotation rotation, BlockType blockType, byte data) {
        IBlockData blockData =  CraftMagicNumbers.getBlock(blockType).fromLegacyData(data);
        blockData = blockData.a(ROTATION[rotation.ordinal()]);
        World world = location.getExtent();
        Vector3i blockPosition = locationToBlockPosition(sponge2MovecraftLoc(location));
        setBlockFast(world,blockPosition,blockData);
    }

    public void disableShadow(BlockType blockType) {
        Method method;
        try {
            Block tempBlock = CraftMagicNumbers.getBlock(blockType.getId());
            method = Block.class.getDeclaredMethod("e", int.class);
            method.setAccessible(true);
            method.invoke(tempBlock, 0);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalArgumentException | IllegalAccessException | SecurityException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }

    private static MovecraftLocation sponge2MovecraftLoc(Location<World> worldLocation) {
        return new MovecraftLocation(worldLocation.getBlockX(), worldLocation.getBlockY(), worldLocation.getBlockZ());
    }

    private void moveTileEntity(World nativeWorld, Vector3i newBlockPosition, TileEntity tile){
        Chunk chunk = nativeWorld.getChunkAtBlock(newBlockPosition);
        tile.invalidateBlockCache();
        if(nativeWorld.captureBlockStates) {
            tile.a(nativeWorld);
            tile.setPosition(newBlockPosition);
            nativeWorld.capturedTileEntities.put(newBlockPosition, tile);
            return;
        }
        tile.setPosition(newBlockPosition);
        chunk.tileEntities.put(newBlockPosition, tile);
    }

    private class TileHolder{
        private final TileEntity tile;

        private final NextTickListEntry nextTick;
        private final Vector3i tilePosition;

        public TileHolder(TileEntity tile, NextTickListEntry nextTick, Vector3i tilePosition){
            this.tile = tile;
            this.nextTick = nextTick;
            this.tilePosition = tilePosition;
        }


        public TileEntity getTile() {
            return tile;
        }

        public NextTickListEntry getNextTick() {
            return nextTick;
        }

        public Vector3i getTilePosition() {
            return tilePosition;
        }
    }
}