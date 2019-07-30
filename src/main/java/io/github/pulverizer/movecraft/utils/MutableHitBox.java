package io.github.pulverizer.movecraft.utils;


import com.flowpowered.math.vector.Vector3i;
import java.util.Collection;

public interface MutableHitBox extends HitBox{
    public boolean add(Vector3i location);
    public boolean addAll(Collection<? extends Vector3i> collection);
    public boolean addAll(HitBox hitBox);
    public boolean remove(Vector3i location);
    public boolean removeAll(Collection<? extends Vector3i> collection);
    public boolean removeAll(HitBox hitBox);
    public void clear();
}