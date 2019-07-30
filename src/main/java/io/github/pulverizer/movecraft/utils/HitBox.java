package io.github.pulverizer.movecraft.utils;

import com.flowpowered.math.vector.Vector3i;
import io.github.pulverizer.movecraft.exception.EmptyHitBoxException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public interface HitBox extends Iterable<Vector3i>{

    public int getMinX();
    public int getMinY();
    public int getMinZ();
    public int getMaxX();
    public int getMaxY();
    public int getMaxZ();

    default public int getXLength(){
        if(this.isEmpty()){
            return 0;
        }
        return Math.abs(this.getMaxX()-this.getMinX());
    }
    default public int getYLength(){
        if(this.isEmpty()){
            return 0;
        }
        return Math.abs(this.getMaxY()-this.getMinY());
    }
    default public int getZLength(){
        if(this.isEmpty()){
            return 0;
        }
        return Math.abs(this.getMaxZ()-this.getMinZ());
    }

    default public boolean isEmpty(){
        return this.size() == 0;
    }
    public int size();

    default public Vector3i getMidPoint(){
        if(this.isEmpty()){
            throw new EmptyHitBoxException();
        }
        return new Vector3i((this.getMinX()+this.getMaxX())/2, (this.getMinY()+this.getMinY())/2,(this.getMinZ()+this.getMaxZ())/2);
    }

    @Override
    public Iterator<Vector3i> iterator();

    public boolean contains(Vector3i location);

    default boolean contains(int x, int y, int z){
        return this.contains(new Vector3i(x,y,z));
    }

    boolean containsAll(Collection<? extends Vector3i> collection);

    default boolean inBounds(double x, double y, double z){
        if(this.isEmpty()){
            return false;
        }
        return x >= this.getMinX() && x <= this.getMaxX() &&
                y >= this.getMinY() && y <= this.getMaxY()&&
                z >= this.getMinZ() && z <= this.getMaxZ();
    }

    default boolean inBounds(Vector3i location){
        return this.inBounds(location.getX(),location.getY(),location.getZ());
    }

    default SolidHitBox boundingHitBox(){
        return new SolidHitBox(new Vector3i(this.getMinX(),this.getMinY(),this.getMinZ()),
                new Vector3i(this.getMaxX(),this.getMaxY(),this.getMaxZ()));
    }

    default Set<Vector3i> asSet(){
        Set<Vector3i> output = new HashSet<>();
        for(Vector3i location : this){
            output.add(location);
        }
        return output;
    }
}