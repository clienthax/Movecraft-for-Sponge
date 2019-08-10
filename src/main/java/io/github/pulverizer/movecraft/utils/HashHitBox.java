package io.github.pulverizer.movecraft.utils;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.exception.EmptyHitBoxException;

import java.util.*;

public class HashHitBox implements MutableHitBox {
    private final Set<Vector3i> locationSet = new HashSet<>();
    private int minX,maxX,minY,maxY,minZ,maxZ;

    public HashHitBox(){
    }

    public HashHitBox(Collection<? extends Vector3i> collection){
        this.addAll(collection);
    }
    public HashHitBox(HitBox hitBox){
        this.addAll(hitBox);
    }

    public int getMinX() {
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return minX;
    }

    public int getMaxX() {
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return maxX;
    }

    public int getMinY() {
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return minY;
    }

    public int getMaxY() {
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return maxY;
    }

    public int getMinZ() {
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return minZ;
    }

    public int getMaxZ() {
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return maxZ;
    }

    public Vector3i getMinPosition() {
        return new Vector3i(minX, minY, minZ);
    }

    public Vector3i getMaxPosition() {
        return new Vector3i(maxX, maxY, maxZ);
    }

    public int getXLength(){
        if(locationSet.isEmpty()){
            return 0;
        }
        return Math.abs(maxX-minX);
    }

    public int getYLength(){
        if(locationSet.isEmpty()){
            return 0;
        }
        return maxY-minY;
    }

    public int getZLength(){
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return Math.abs(maxZ-minZ);
    }

    public Vector3i get3dSize() {
        if (locationSet.isEmpty())
            throw new EmptyHitBoxException();

        int x = Math.abs(maxX-minX);
        int y = Math.abs(maxY-minY);
        int z = Math.abs(maxZ-minZ);
        return new Vector3i(x, y, z);
    }

    //TODO: Optomize
    public int getLocalMaxY(int x, int z){
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        int yValue=-1;
        for(Vector3i location : locationSet){
            if(location.getX()==x && location.getZ() ==z && location.getY()>yValue){
                yValue=location.getY();
            }
        }
        return yValue;
    }

    public int getLocalMinY(int x, int z){
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        int yValue=-1;
        for(Vector3i location : locationSet){
            if(location.getX()==x && location.getZ() ==z && (yValue==-1 || location.getY()>yValue)){
                yValue=location.getY();
            }
        }
        return yValue;
    }

    public Vector3i getMidPoint(){
        if(locationSet.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return new Vector3i((minX+maxX)/2, (minY+maxY)/2,(minZ+maxZ)/2);
    }

    public boolean inBounds(Vector3i location){
        if(locationSet.isEmpty()){
            return false;
        }
        return location.getX() >= minX && location.getX() <= maxX &&
                location.getY() >= minY && location.getY() <= maxY &&
                location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    public boolean inBounds(double x, double y, double z){
        if(locationSet.isEmpty()){
            return false;
        }
        return x >= minX && x <= maxX &&
                y >= minY && y <= maxY &&
                z >= minZ && z <= maxZ;
    }

    public boolean intersects(HitBox hitBox){
        for(Vector3i location : hitBox){
            if(this.contains(location)){
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        return locationSet.size();
    }

    @Override
    public boolean isEmpty() {
        return locationSet.isEmpty();
    }

    @Override
    public boolean contains(Vector3i location) {
        return locationSet.contains(location);
    }

    public boolean contains(int x, int y, int z){
        return contains(new Vector3i(x,y,z));
    }

    @Override
    public Iterator<Vector3i> iterator(){
        return new Iterator<Vector3i>() {

            private final Iterator<Vector3i> it = locationSet.iterator();
            private Vector3i last;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Vector3i next() {
                return last = it.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
                /*if (last == null) {
                    throw new IllegalStateException();
                }
                it.remove();
                last = null;*/
            }
        };
    }

    @Override
    public boolean add(Vector3i vector3i) {
        if(locationSet.isEmpty() || vector3i.getX() < minX)
            minX= vector3i.getX();
        if(locationSet.isEmpty() || vector3i.getX() > maxX)
            maxX= vector3i.getX();
        if(locationSet.isEmpty() || vector3i.getY() < minY)
            minY= vector3i.getY();
        if(locationSet.isEmpty() || vector3i.getY() > maxY)
            maxY= vector3i.getY();
        if(locationSet.isEmpty() || vector3i.getZ() < minZ)
            minZ= vector3i.getZ();
        if(locationSet.isEmpty() || vector3i.getZ() > maxZ)
            maxZ= vector3i.getZ();
        return locationSet.add(vector3i);
    }

    @Override
    public boolean remove(Vector3i location) {
        if(!locationSet.contains(location))
            return false;
        locationSet.remove(location);
        if(minX==location.getX() || maxX == location.getX() || minY == location.getY() || maxY==location.getY() || minZ==location.getZ() || maxZ==location.getZ()){
            updateBounds();
        }
        return true;
    }

    @Override
    public boolean containsAll(Collection<? extends Vector3i> c) {
        return locationSet.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Vector3i> c) {
        boolean modified = false;
        for (Vector3i location : c) {
            if (add(location))
                modified = true;
        }
        return modified;
    }

    @Override
    public boolean addAll(HitBox hitBox) {

        if (hitBox == null)
            return true;

        boolean modified = false;
        for (Vector3i location : hitBox) {
            if (add(location))
                modified = true;
        }
        return modified;
    }

    @Override
    public boolean removeAll(Collection<? extends Vector3i> c) {
        boolean updateBounds = false;
        boolean modified = false;
        for(Vector3i location : c){
            if(locationSet.remove(location)) {
                modified = true;
                if (location.getX() < minX)
                    updateBounds=true;
                if (location.getX() > maxX)
                    updateBounds=true;
                if (location.getY() < minY)
                    updateBounds=true;
                if (location.getY() > maxY)
                    updateBounds=true;
                if (location.getZ() < minZ)
                    updateBounds=true;
                if (location.getZ() > maxZ)
                    updateBounds=true;
            }
        }
        if(updateBounds){
            updateBounds();
        }
        return modified;
    }

    @Override
    public boolean removeAll(HitBox hitBox) {
        boolean updateBounds = false;
        boolean modified = false;
        for(Vector3i location : hitBox){
            if(locationSet.remove(location)) {
                modified = true;
                if (location.getX() < minX)
                    updateBounds=true;
                if (location.getX() > maxX)
                    updateBounds=true;
                if (location.getY() < minY)
                    updateBounds=true;
                if (location.getY() > maxY)
                    updateBounds=true;
                if (location.getZ() < minZ)
                    updateBounds=true;
                if (location.getZ() > maxZ)
                    updateBounds=true;
            }
        }
        if(updateBounds){
            updateBounds();
        }
        return modified;
    }


    @Override
    public void clear() {
        locationSet.clear();
    }

    private final static Vector3i[] SHIFTS = {
            new Vector3i(0, 0, 1),
            new Vector3i(0, 1, 0),
            new Vector3i(1, 0 ,0),
            new Vector3i(0, 0, -1),
            new Vector3i(0, -1, 0),
            new Vector3i(-1, 0, 0)};
    /**
     * finds the axial neighbors to a location. Neighbors are defined as locations that exist within one meter of a given
     * location
     * @param location the location to search for neighbors
     * @return an iterable set of neighbors to the given location
     */

    public Iterable<Vector3i> neighbors(Vector3i location){
        if(this.isEmpty()){
            return Collections.emptyList();
        }
        final List<Vector3i> neighbors = new ArrayList<>(6);
        for(Vector3i test : SHIFTS){
            if(this.contains(location.add(test))){
                neighbors.add(location.add(test));
            }
        }
        return neighbors;
    }

    /**
     * Gets a HitBox that represents the "exterior" of this HitBox. The exterior is defined as the region of all
     * location accessible from the six bounding planes of the hitbox before encountering a location contained in the
     * original HitBox. Functions similarly to a flood fill but in three dimensions
     * @return the exterior HitBox
     */
    public HashHitBox exterior(){
        return null;
    }

    private void updateBounds(){
        for (Vector3i location : locationSet){
            if(location.getX()<minX)
                minX=location.getX();
            if(location.getX()>maxX)
                maxX=location.getX();
            if(location.getY()<minY)
                minY=location.getY();
            if(location.getY()>maxY)
                maxY=location.getY();
            if(location.getZ()<minZ)
                minZ=location.getZ();
            if(location.getZ()>maxZ)
                maxZ=location.getZ();
        }
    }
}