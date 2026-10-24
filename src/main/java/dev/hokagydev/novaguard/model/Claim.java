package dev.hokagydev.novaguard.model;

import org.bukkit.Location;
import java.util.*;

public final class Claim {
    public final String id, name, world;
    public final UUID owner;
    public final int minX,minY,minZ,maxX,maxY,maxZ;
    public final Set<UUID> members = new HashSet<>();
    public final Map<String,String> flags = new HashMap<>();
    public int priority;
    public String greeting = "", farewell = "";

    public Claim(String id,String name,UUID owner,String world,int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        this.id=id; this.name=name; this.owner=owner; this.world=world;
        this.minX=minX; this.minY=minY; this.minZ=minZ; this.maxX=maxX; this.maxY=maxY; this.maxZ=maxZ;
    }
    public boolean contains(Location l) {
        return l != null && l.getWorld()!=null && world.equals(l.getWorld().getName()) &&
            l.getBlockX()>=minX && l.getBlockX()<=maxX && l.getBlockY()>=minY && l.getBlockY()<=maxY &&
            l.getBlockZ()>=minZ && l.getBlockZ()<=maxZ;
    }
    public boolean overlaps(Claim c) {
        return world.equals(c.world) && minX<=c.maxX && maxX>=c.minX && minY<=c.maxY && maxY>=c.minY && minZ<=c.maxZ && maxZ>=c.minZ;
    }
    public long volume() {
        try { return Math.multiplyExact(Math.multiplyExact((long)maxX-minX+1,(long)maxY-minY+1),(long)maxZ-minZ+1); }
        catch (ArithmeticException ex) { return Long.MAX_VALUE; }
    }
    public boolean trusted(UUID uuid) { return owner.equals(uuid) || members.contains(uuid); }
}
