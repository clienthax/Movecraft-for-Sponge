package io.github.pulverizer.movecraft.mixin;

import com.google.common.collect.Lists;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Iterator;
import java.util.List;

@Mixin(PlayerChunkMap.class)
public abstract class PlayerChunkMapMixin {

    @Shadow private int playerViewRadius;
    @Shadow @Final private List<EntityPlayerMP> players;

    @Shadow protected abstract PlayerChunkMapEntry getOrCreateEntry(int chunkX, int chunkZ);
    @Shadow protected abstract boolean overlaps(int x1, int z1, int x2, int z2, int radius);
    @Shadow protected abstract void markSortPending();

    /**
     * Removes the 32 chunk view distance limit.
     * @author BernardisGood
     */
    @Overwrite
    public void setPlayerViewRadius(int radius) {
        if (radius != this.playerViewRadius) {
            int i = radius - this.playerViewRadius;
            Iterator var3 = Lists.newArrayList(this.players).iterator();

            while(true) {
                while(var3.hasNext()) {
                    EntityPlayerMP entityplayermp = (EntityPlayerMP)var3.next();
                    int j = (int)entityplayermp.posX >> 4;
                    int k = (int)entityplayermp.posZ >> 4;
                    int l;
                    int i1;
                    if (i > 0) {
                        for(l = j - radius; l <= j + radius; ++l) {
                            for(i1 = k - radius; i1 <= k + radius; ++i1) {
                                PlayerChunkMapEntry playerchunkmapentry = this.getOrCreateEntry(l, i1);
                                if (!playerchunkmapentry.containsPlayer(entityplayermp)) {
                                    playerchunkmapentry.addPlayer(entityplayermp);
                                }
                            }
                        }
                    } else {
                        for(l = j - this.playerViewRadius; l <= j + this.playerViewRadius; ++l) {
                            for(i1 = k - this.playerViewRadius; i1 <= k + this.playerViewRadius; ++i1) {
                                if (!this.overlaps(l, i1, j, k, radius)) {
                                    this.getOrCreateEntry(l, i1).removePlayer(entityplayermp);
                                }
                            }
                        }
                    }
                }

                this.playerViewRadius = radius;
                this.markSortPending();
                break;
            }
        }
    }
}

