package io.github.pulverizer.movecraft.utils;


import io.github.pulverizer.movecraft.Movecraft;
import org.slf4j.Logger;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.util.rotation.Rotation;

public class SignUtils {
    /**
     * @param sign
     * @return
     */
    public static Rotation getFacing(Sign sign) {
        BlockSnapshot block = sign.getLocation().createSnapshot();
        Rotation rotation = null;
        Logger logger = Movecraft.getInstance().getLogger();

        if (block.getExtendedState().supports(Keys.ROTATION)) {
            rotation = block.getExtendedState().get(Keys.ROTATION).get();
        }

        if (rotation == null) {
            logger.error("Fatal Exception - Sign Rotation Not Found!");
        }
        return rotation;
    }
}
