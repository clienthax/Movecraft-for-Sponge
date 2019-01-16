package io.github.pulverizer.movecraft.craft;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.localisation.I18nSupport;
import org.spongepowered.api.entity.living.player.Player;
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
    private final ConcurrentMap<Craft, BukkitTask> releaseEvents = new ConcurrentHashMap<>();
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
        File craftsFile = new File(Movecraft.getInstance().getDataFolder().getAbsolutePath() + "/types");

        if (craftsFile.mkdirs()) {
            Movecraft.getInstance().saveResource("types/airship.craft", false);
            Movecraft.getInstance().saveResource("types/airskiff.craft", false);
            Movecraft.getInstance().saveResource("types/BigAirship.craft", false);
            Movecraft.getInstance().saveResource("types/BigSubAirship.craft", false);
            Movecraft.getInstance().saveResource("types/elevator.craft", false);
            Movecraft.getInstance().saveResource("types/LaunchTorpedo.craft", false);
            Movecraft.getInstance().saveResource("types/Ship.craft", false);
            Movecraft.getInstance().saveResource("types/SubAirship.craft", false);
            Movecraft.getInstance().saveResource("types/Submarine.craft", false);
            Movecraft.getInstance().saveResource("types/Turret.craft", false);
        }

        Set<CraftType> craftTypes = new HashSet<>();
        File[] files = craftsFile.listFiles();
        if (files == null){
            return craftTypes;
        }

        for (File file : files) {
            if (file.isFile()) {

                if (file.getName().contains(".craft")) {
                    CraftType type = new CraftType(file);
                    craftTypes.add(type);
                }
            }
        }
        if (craftTypes.isEmpty()) {
            Movecraft.getInstance().getLogger().warn("ERROR: NO CRAFTS FOUND!");
        }
        Movecraft.getInstance().getLogger().info(String.format(I18nSupport.getInternationalisedString("Startup - Number of craft files loaded"), craftTypes.size()));
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
                player.sendMessage(Text.of(I18nSupport.getInternationalisedString("Release - Craft has been released message")));
                Movecraft.getInstance().getLogger().info(String.format(I18nSupport.getInternationalisedString("Release - Player has released a craft console"), c.getNotificationPlayer().getName(), c.getType().getCraftName(), c.getHitBox().size(), c.getHitBox().getMinX(), c.getHitBox().getMinZ()));
            } else {
                Movecraft.getInstance().getLogger().info(String.format(I18nSupport.getInternationalisedString("NULL Player has released a craft of type %s with size %d at coordinates : %d x , %d z"), c.getType().getCraftName(), c.getHitBox().size(), c.getHitBox().getMinX(), c.getHitBox().getMinZ()));
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
            if(c.getW() == w)
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
            if(c.getNotificationPlayer() != null && c.getNotificationPlayer().equals(player)){
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
        p.sendMessage(Text.of(I18nSupport.getInternationalisedString("Release - Craft has been released message")));
        Movecraft.getInstance().getLogger().info(String.format(I18nSupport.getInternationalisedString("Release - Player has released a craft console"), p.getName(), c.getType().getCraftName(), c.getHitBox().size(), c.getHitBox().getMinX(), c.getHitBox().getMinZ()));
        c.setNotificationPlayer(null);
        craftPlayerIndex.remove(p);
    }


    @Deprecated
    public final void addReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (p!=null) {
            p.sendMessage(Text.of(I18nSupport.getInternationalisedString("Release - Player has left craft")));
        }
        BukkitTask releaseTask = new BukkitRunnable() {
            @Override
            public void run() {
                removeCraft(c);
            }
        }.runTaskLater(Movecraft.getInstance(), (20 * 15));
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