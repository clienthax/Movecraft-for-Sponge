package io.github.pulverizer.movecraft.craft;

import io.github.pulverizer.movecraft.Movecraft;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CraftManager implements Iterable<Craft>{
    private static CraftManager ourInstance;
    private final Set<Craft> craftList = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Player, Craft> craftPlayerIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<Craft, Task> releaseEvents = new ConcurrentHashMap<>();
    private Set<CraftType> craftTypes;

    public static void initialize(){
        ourInstance = new CraftManager();
    }

    private CraftManager() {
        this.craftTypes = loadCraftTypes();
    }

    public static CraftManager getInstance() {
        return ourInstance;
    }

    public Set<CraftType> getCraftTypes() {
        return Collections.unmodifiableSet(craftTypes);
    }

    private Set<CraftType> loadCraftTypes(){
        File craftsFile = Movecraft.getInstance().getConfigDir().resolve("types").toFile();

        Set<CraftType> craftTypes = new HashSet<>();
        File[] files = craftsFile.listFiles();
        if (files == null){
            Movecraft.getInstance().getLogger().error("No CraftTypes Found!");
            return craftTypes;
        }

        for (File file : files) {
            if (file.isFile()) {

                if (file.getName().contains(".craft")) {
                    Movecraft.getInstance().getLogger().info("Loading CraftType: " + file.getName());
                    CraftType type = new CraftType(file);
                    craftTypes.add(type);
                }
            }
        }

        Movecraft.getInstance().getLogger().info("Loaded " + craftTypes.size() + " CraftTypes.");
        return craftTypes;
    }

    public void initCraftTypes() {
        this.craftTypes = loadCraftTypes();
    }

    public void addCraft(Craft c, Player p) {
        this.craftList.add(c);
        if(p!=null)
            this.craftPlayerIndex.put(p, c);
    }

    public void removeCraft(Craft c) {
        removeReleaseTask(c);
        Player player = getPlayerFromCraft(c);
        if (player!=null)
            this.craftPlayerIndex.remove(player);
        // if its sinking, just remove the craft without notifying or checking
        this.craftList.remove(c);
        if(!c.getHitBox().isEmpty()) {
            if (player != null) {
                player.sendMessage(Text.of("You have released your craft."));
                Movecraft.getInstance().getLogger().info(String.format(c.getPilot().getName() + " has released a craft of type %s with size %d at coordinates : %d x , %d z", c.getType().getCraftName(), c.getHitBox().size(), c.getHitBox().getMinX(), c.getHitBox().getMinZ()));
            } else {
                Movecraft.getInstance().getLogger().info(String.format("NULL Player has released a craft of type %s with size %d at coordinates : %d x , %d z", c.getType().getCraftName(), c.getHitBox().size(), c.getHitBox().getMinX(), c.getHitBox().getMinZ()));
            }
        }else{
            Movecraft.getInstance().getLogger().warn("Releasing empty craft!");
        }
    }

    public void forceRemoveCraft(Craft c) {
        this.craftList.remove(c);
        if (getPlayerFromCraft(c) != null)
            this.craftPlayerIndex.remove(getPlayerFromCraft(c));
    }

    public Set<Craft> getCraftsInWorld(World w) {
        Set<Craft> crafts = new HashSet<>();
        for(Craft c : this.craftList){
            if(c.getWorld() == w)
                crafts.add(c);
        }
        return crafts;
    }

    public Craft getCraftByPlayer(Player p) {
        if(p == null)
            return null;
        return craftPlayerIndex.get(p);
    }


    public Craft getCraftByPlayerName(String name) {
        Set<Player> players = craftPlayerIndex.keySet();
        for (Player player : players) {
            if (player != null && player.getName().equals(name)) {
                return this.craftPlayerIndex.get(player);
            }
        }
        return null;
    }

    public void removeCraftByPlayer(Player player){
        List<Craft> crafts = new ArrayList<>();
        for(Craft c : craftList){
            if(c.getPilot() != null && c.getPilot().equals(player)){
                releaseEvents.remove(c);
                crafts.add(c);
            }
        }
        craftPlayerIndex.remove(player);
        craftList.removeAll(crafts);
    }

    public Player getPlayerFromCraft(Craft c) {
        for (Map.Entry<Player, Craft> playerCraftEntry : craftPlayerIndex.entrySet()) {
            if (playerCraftEntry.getValue() == c) {
                return playerCraftEntry.getKey();
            }
        }
        return null;
    }

    public void removePlayerFromCraft(Craft c) {
        if (getPlayerFromCraft(c) == null) {
            return;
        }
        removeReleaseTask(c);
        Player p = getPlayerFromCraft(c);
        p.sendMessage(Text.of("You have released your craft."));
        Movecraft.getInstance().getLogger().info(String.format(p.getName() + " has released a craft of type %s with size %d at coordinates : %d x , %d z", c.getType().getCraftName(), c.getHitBox().size(), c.getHitBox().getMinX(), c.getHitBox().getMinZ()));
        c.setPilot(null);
        craftPlayerIndex.remove(p);
    }


    @Deprecated
    public final void addReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (p!=null) {
            p.sendMessage(Text.of("You have released your craft."));
        }

        Task releaseTask = Task.builder().delayTicks(20*15).execute(() -> removeCraft(c)).submit(Movecraft.getInstance());
        releaseEvents.put(c, releaseTask);

    }

    @Deprecated
    public final void removeReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (p != null) {
            if (releaseEvents.containsKey(c)) {
                if (releaseEvents.get(c) != null)
                    releaseEvents.get(c).cancel();
                releaseEvents.remove(c);
            }
        }
    }

    @Deprecated
    public boolean isReleasing(final Craft craft){
        return releaseEvents.containsKey(craft);
    }

    @Deprecated
    public Set<Craft> getCraftList(){
        return Collections.unmodifiableSet(craftList);
    }

    public CraftType getCraftTypeFromString(String s) {
        for (CraftType t : craftTypes) {
            if (s.equalsIgnoreCase(t.getCraftName())) {
                return t;
            }
        }
        return null;
    }

    public boolean isEmpty(){
        return this.craftList.isEmpty();
    }

    @Override
    public Iterator<Craft> iterator() {
        return Collections.unmodifiableSet(this.craftList).iterator();
    }
}