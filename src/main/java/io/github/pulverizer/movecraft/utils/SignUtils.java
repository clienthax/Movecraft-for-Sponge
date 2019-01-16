package io.github.pulverizer.movecraft.utils;


import org.spongepowered.api.block.tileentity.Sign;

public class SignUtils {
    /**
     * @param sign
     * @return
     */
    public static BlockFace getFacing(Sign sign) {
        MaterialData materialData = sign.getData();
        Sign matSign = (Sign) materialData;
        return matSign.getFacing();
    }
}
