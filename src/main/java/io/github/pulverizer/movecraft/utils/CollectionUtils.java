package io.github.pulverizer.movecraft.utils;

import io.github.pulverizer.movecraft.MovecraftLocation;

import java.util.*;

public class CollectionUtils {

    /**
     * Removes the elements from <code>collection</code> that also exist in <code>filter</code> without modifying either.
     * @param collection
     * @param filter
     * @param <E>  the element type
     * @return a <code>Collection</code> containing all the elements of <code>collection</code> except those in <code>filter</code>
     */
    public static <E> Collection<E> filter(final Collection<E> collection, final Collection<E> filter){
        final Collection<E> returnList = new HashSet<>();
        final HashSet<E> filterSet = new HashSet<>(filter);
        for(E object : collection){
            if(!filterSet.contains(object)){
                returnList.add(object);
            }
        }
        return returnList;
    }

    public static Collection<MovecraftLocation> filter(final HitBox collection, final Collection<MovecraftLocation> filter){
        final Collection<MovecraftLocation> returnList = new HashSet<>();
        final HashSet<MovecraftLocation> filterSet = new HashSet<>(filter);
        for(MovecraftLocation object : collection){
            if(!filterSet.contains(object)){
                returnList.add(object);
            }
        }
        return returnList;
    }

    public static HitBox filter(final HitBox collection, final HitBox filter){
        final MutableHitBox returnList = new HashHitBox();
        for(MovecraftLocation object : collection){
            if(!filter.contains(object)){
                returnList.add(object);
            }
        }
        return returnList;
    }

    private final static MovecraftLocation[] SHIFTS = {
            new MovecraftLocation(0, 0, 1),
            new MovecraftLocation(0, 1, 0),
            new MovecraftLocation(1, 0 ,0),
            new MovecraftLocation(0, 0, -1),
            new MovecraftLocation(0, -1, 0),
            new MovecraftLocation(-1, 0, 0)};

    /**
     * Finds the axial neighbors to a location. Neighbors are defined as locations that exist within one meter of a given location.
     * @param hitbox
     * @param location the location to search for neighbors
     * @return an iterable set of neighbors to the given location
     */
    public static Iterable<MovecraftLocation> neighbors(HitBox hitbox, MovecraftLocation location){
        if(hitbox.isEmpty()){
            return Collections.emptyList();
        }
        final List<MovecraftLocation> neighbors = new ArrayList<>(6);
        for(MovecraftLocation test : SHIFTS){
            if(hitbox.contains(location.add(test))){
                neighbors.add(location.add(test));
            }
        }
        return neighbors;
    }
}