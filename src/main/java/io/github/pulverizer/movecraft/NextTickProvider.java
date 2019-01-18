package io.github.pulverizer.movecraft;

import com.flowpowered.math.vector.Vector3i;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.NextTickListEntry;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.apache.commons.lang3.tuple.ImmutablePair;

import org.bukkit.craftbukkit.v1_12_R1.util.HashTreeSet;
import org.spongepowered.api.world.World;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NextTickProvider0 {
    private Map<World,ImmutablePair<HashTreeSet<NextTickListEntry>,List<NextTickListEntry>>> tickMap = new HashMap<>();

    private boolean isRegistered(World world){
        return tickMap.containsKey(world);
    }

    @SuppressWarnings("unchecked")
    private void registerWorld(World world){
        List<NextTickListEntry> W = new ArrayList<>();
        HashTreeSet<NextTickListEntry> nextTickList = new HashTreeSet<>();

        try {

            Field WField = World.class.getDeclaredField("W");
            WField.setAccessible(true);
            W = (List<NextTickListEntry>) WField.get(world);
            Field nextTickListField = World.class.getDeclaredField("nextTickList");
            nextTickListField.setAccessible(true);
            nextTickList = (HashTreeSet<NextTickListEntry>) nextTickListField.get(world);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e1) {
            e1.printStackTrace();
        }
        tickMap.put(world, new ImmutablePair<>(nextTickList,W));
    }

    public NextTickListEntry getNextTick(World world, Vector3i blockPosition){
        if(!isRegistered(world))
            registerWorld(world);
        ImmutablePair<HashTreeSet<NextTickListEntry>, List<NextTickListEntry>> listPair = tickMap.get(world);
        for(Iterator<NextTickListEntry> iterator = listPair.left.iterator(); iterator.hasNext();) {
            NextTickListEntry listEntry = iterator.next();
            if (blockPosition.equals(listEntry.a)) {
                iterator.remove();
                return listEntry;
            }
        }
        for(Iterator<NextTickListEntry> iterator = listPair.right.iterator(); iterator.hasNext();) {
            NextTickListEntry listEntry = iterator.next();
            if (blockPosition.equals(listEntry.a)) {
                iterator.remove();
                return listEntry;
            }
        }
        return null;

    }
}