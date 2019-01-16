package io.github.pulverizer.movecraft.mapUpdater.update;

import io.github.pulverizer.movecraft.Movecraft;
import org.bukkit.scheduler.BukkitRunnable;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Objects;

/**   Class that stores the data about a item drops to the map in an unspecified world. The world is retrieved contextually from the submitting craft.   */
public class ItemDropUpdateCommand extends UpdateCommand {
    private final Location location;
    private final ItemStack itemStack;

    public ItemDropUpdateCommand(Location location, ItemStack itemStack) {
        this.location = location;
        this.itemStack = itemStack;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public Location getLocation() {
        return location;
    }

    @Override
    public void doUpdate() {
        if (itemStack != null) {
            final World world = location.getWorld();
            // drop Item
            new BukkitRunnable() {
                @Override
                public void run() {
                    world.dropItemNaturally(ItemDropUpdateCommand.this.location, itemStack);
                }
            }.runTaskLater(Movecraft.getInstance(), 20);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, itemStack);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof ItemDropUpdateCommand)){
            return false;
        }
        ItemDropUpdateCommand other = (ItemDropUpdateCommand) obj;
        return this.location.equals(other.location) &&
                this.itemStack.equals(other.itemStack);
    }

}
