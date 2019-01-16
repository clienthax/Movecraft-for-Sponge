package io.github.pulverizer.movecraft.utils;

import io.github.pulverizer.movecraft.MovecraftLocation;

import java.util.Collection;

public interface MutableHitBox extends HitBox{
    public boolean add(MovecraftLocation location);
    public boolean addAll(Collection<? extends MovecraftLocation> collection);
    public boolean addAll(HitBox hitBox);
    public boolean remove(MovecraftLocation location);
    public boolean removeAll(Collection<? extends MovecraftLocation> collection);
    public boolean removeAll(HitBox hitBox);
    public void clear();
}