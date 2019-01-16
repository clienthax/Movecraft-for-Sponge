package io.github.pulverizer.movecraft.utils;

import io.github.pulverizer.movecraft.Rotation;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;

import java.util.Arrays;

public class BlockUtils {
    private static final BlockType[] dataBlocks = new BlockType[]{BlockTypes.GRASS, BlockTypes.DIRT, BlockTypes.PLANKS, BlockTypes.SAPLING, BlockTypes.FLOWING_WATER, BlockTypes.WATER, BlockTypes.FLOWING_LAVA, BlockTypes.LAVA, BlockTypes.LOG, BlockTypes.LEAVES, BlockTypes.DISPENSER, BlockTypes.SANDSTONE, BlockTypes.RED_SANDSTONE, BlockTypes.NOTEBLOCK, BlockTypes.BED, BlockTypes.GOLDEN_RAIL, BlockTypes.DETECTOR_RAIL, BlockTypes.STICKY_PISTON, BlockTypes.TALLGRASS, BlockTypes.PISTON, BlockTypes.PISTON_HEAD, BlockTypes.WOOL, BlockTypes.DOUBLE_STONE_SLAB, BlockTypes.STONE_SLAB, BlockTypes.TNT, BlockTypes.TORCH, BlockTypes.FIRE, BlockTypes.MOB_SPAWNER, BlockTypes.OAK_STAIRS, BlockTypes.REDSTONE_WIRE, BlockTypes.WHEAT, BlockTypes.FARMLAND, BlockTypes.FURNACE, BlockTypes.LIT_FURNACE, BlockTypes.STANDING_SIGN, BlockTypes.WOODEN_DOOR, BlockTypes.LADDER, BlockTypes.RAIL, BlockTypes.STONE_STAIRS, BlockTypes.WALL_SIGN, BlockTypes.LEVER, BlockTypes.STONE_PRESSURE_PLATE, BlockTypes.IRON_DOOR, BlockTypes.WOODEN_PRESSURE_PLATE, BlockTypes.UNLIT_REDSTONE_TORCH, BlockTypes.REDSTONE_TORCH, BlockTypes.STONE_BUTTON, BlockTypes.SNOW_LAYER, BlockTypes.CACTUS, BlockTypes.REEDS, BlockTypes.JUKEBOX, BlockTypes.PUMPKIN, BlockTypes.LIT_PUMPKIN, BlockTypes.CAKE, BlockTypes.UNPOWERED_REPEATER, BlockTypes.POWERED_REPEATER, BlockTypes.TRAPDOOR, BlockTypes.STONEBRICK, BlockTypes.BROWN_MUSHROOM_BLOCK, BlockTypes.RED_MUSHROOM_BLOCK, BlockTypes.PUMPKIN_STEM, BlockTypes.MELON_STEM, BlockTypes.VINE, BlockTypes.FENCE_GATE, BlockTypes.BRICK_STAIRS, BlockTypes.STONE_BRICK_STAIRS, BlockTypes.NETHER_BRICK_STAIRS, BlockTypes.NETHER_WART, BlockTypes.ENCHANTING_TABLE, BlockTypes.BREWING_STAND, BlockTypes.CAULDRON, BlockTypes.END_PORTAL_FRAME, BlockTypes.DOUBLE_WOODEN_SLAB, BlockTypes.WOODEN_SLAB, BlockTypes.COCOA, BlockTypes.SANDSTONE_STAIRS, BlockTypes.RED_SANDSTONE_STAIRS, BlockTypes.ENDER_CHEST, BlockTypes.TRIPWIRE, BlockTypes.TRIPWIRE_HOOK, BlockTypes.SPRUCE_STAIRS, BlockTypes.BIRCH_STAIRS, BlockTypes.JUNGLE_STAIRS, BlockTypes.COMMAND_BLOCK, BlockTypes.CHAIN_COMMAND_BLOCK, BlockTypes.REPEATING_COMMAND_BLOCK, BlockTypes.BEACON, BlockTypes.COBBLESTONE_WALL, BlockTypes.FLOWER_POT, BlockTypes.CARROTS, BlockTypes.POTATOES, BlockTypes.WOODEN_BUTTON, BlockTypes.SKULL, BlockTypes.ANVIL, BlockTypes.TRAPPED_CHEST, BlockTypes.LIGHT_WEIGHTED_PRESSURE_PLATE, BlockTypes.HEAVY_WEIGHTED_PRESSURE_PLATE, BlockTypes.UNPOWERED_COMPARATOR, BlockTypes.POWERED_COMPARATOR, BlockTypes.DAYLIGHT_DETECTOR, BlockTypes.DAYLIGHT_DETECTOR_INVERTED, BlockTypes.HOPPER, BlockTypes.QUARTZ_BLOCK, BlockTypes.QUARTZ_STAIRS, BlockTypes.ACTIVATOR_RAIL, BlockTypes.DROPPER, BlockTypes.STAINED_HARDENED_CLAY, BlockTypes.HAY_BLOCK, BlockTypes.CARPET};

    private static final BlockType[] rotationBlocks = new BlockType[]{BlockTypes.LOG, BlockTypes.TORCH, BlockTypes.CHEST, BlockTypes.UNLIT_REDSTONE_TORCH, BlockTypes.REDSTONE_TORCH, BlockTypes.BED, BlockTypes.STICKY_PISTON, BlockTypes.PISTON, BlockTypes.PISTON_HEAD, BlockTypes.PISTON_EXTENSION, BlockTypes.OAK_STAIRS, BlockTypes.STONE_STAIRS, BlockTypes.BRICK_STAIRS, BlockTypes.STONE_BRICK_STAIRS, BlockTypes.NETHER_BRICK_STAIRS, BlockTypes.SANDSTONE_STAIRS, BlockTypes.SPRUCE_STAIRS, BlockTypes.BIRCH_STAIRS, BlockTypes.JUNGLE_STAIRS, BlockTypes.QUARTZ_STAIRS, BlockTypes.STANDING_SIGN, BlockTypes.WOODEN_DOOR, BlockTypes.IRON_DOOR, BlockTypes.RAIL, BlockTypes.GOLDEN_RAIL, BlockTypes.DETECTOR_RAIL, BlockTypes.LADDER, BlockTypes.WALL_SIGN, BlockTypes.FURNACE, BlockTypes.DISPENSER, BlockTypes.LEVER, BlockTypes.STONE_BUTTON, BlockTypes.WOODEN_BUTTON, BlockTypes.UNPOWERED_REPEATER, BlockTypes.POWERED_REPEATER, BlockTypes.TRAPDOOR, BlockTypes.FENCE_GATE, BlockTypes.END_PORTAL_FRAME, BlockTypes.TRIPWIRE_HOOK, BlockTypes.SKULL,  BlockTypes.ANVIL, BlockTypes.LIT_FURNACE, BlockTypes.BROWN_MUSHROOM_BLOCK, BlockTypes.RED_MUSHROOM_BLOCK, BlockTypes.VINE, BlockTypes.COCOA, BlockTypes.ENDER_CHEST, BlockTypes.UNPOWERED_COMPARATOR, BlockTypes.POWERED_COMPARATOR, BlockTypes.HOPPER, BlockTypes.ACTIVATOR_RAIL, BlockTypes.DROPPER, BlockTypes.HAY_BLOCK, BlockTypes.PUMPKIN, BlockTypes.LIT_PUMPKIN, BlockTypes.ACACIA_STAIRS, BlockTypes.DARK_OAK_STAIRS, BlockTypes.PURPUR_STAIRS};

    static {
        Arrays.sort(dataBlocks);
        Arrays.sort(rotationBlocks);
    }

    public static boolean blockHasNoData(BlockType id) {
        return Arrays.binarySearch(dataBlocks, id) == -1;
    }

    public static boolean blockRequiresRotation(BlockType id) {
        return Arrays.binarySearch(rotationBlocks, id) != -1;
    }

    public static boolean arrayContainsOverlap(Object[] array1, Object[] array2) {
        for (Object o : array1) {

            for (Object o1 : array2) {
                if (o.equals(o1)) {
                    return true;
                }
            }

        }

        return false;
    }

    public static byte rotate(byte data, int typeID, Rotation rotation) {
        switch (typeID) {
            case 17:
            case 170:
                boolean side1 = ((data & 0x4) == 0x4);
                boolean side2 = ((data & 0x8) == 0x8);

                if (side1 || side2) {
                    data = (byte) (data ^ 0xC);
                }
                return data;

            case 50:
            case 75:
            case 76:
                boolean nonDirectional = data == 0x5 || data == 0x6;

                if (!nonDirectional) {
                    switch (data) {
                        case 0x1:
                            if (rotation == Rotation.CLOCKWISE) {
                                data = 0x3;
                            } else {
                                data = 0x4;
                            }
                            break;

                        case 0x2:
                            if (rotation == Rotation.CLOCKWISE) {
                                data = 0x4;
                            } else {
                                data = 0x3;
                            }
                            break;

                        case 0x3:
                            if (rotation == Rotation.CLOCKWISE) {
                                data = 0x02;
                            } else {
                                data = 0x1;
                            }
                            break;

                        case 0x4:
                            if (rotation == Rotation.CLOCKWISE) {
                                data = 0x1;
                            } else {
                                data = 0x2;
                            }
                            break;
                    }
                }

                return data;

            case 26:
            case 127:
            case 149:
            case 150:
                byte direction = (byte) (data & 0x3);

                byte constant = 1;

                if (rotation == Rotation.ANTICLOCKWISE) {
                    constant = -1;
                }

                direction = (byte) (MathUtils.positiveMod((direction + constant) % 4, 4));
                data = (byte) ((data & 0xC) | direction);

                return data;

            case 29:
            case 33:
            case 34:
                direction = (byte) (data & 0x7);

                nonDirectional = direction == 0x0 || direction == 0x1 || direction == 0x6;

                if (!nonDirectional) {
                    switch (direction) {
                        case 0x2:
                            // North
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x5;
                            } else {
                                direction = 0x4;
                            }
                            break;

                        case 0x3:
                            // South
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x4;
                            } else {
                                direction = 0x5;
                            }
                            break;

                        case 0x4:
                            // West
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x2;
                            } else {
                                direction = 0x3;
                            }
                            break;

                        case 0x5:
                            //East
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x3;
                            } else {
                                direction = 0x2;
                            }
                            break;
                    }

                    data = (byte) ((data & 0x8) | direction);
                }

                return data;

            case 53:
            case 67:
            case 108:
            case 109:
            case 114:
            case 128:
            case 134:
            case 135:
            case 136:
            case 156:
            case 163:
            case 164:
            case 203:

                direction = (byte) (data & 0x3);


                switch (direction) {
                    case 0x0:
                        // East
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x2;
                        } else {
                            direction = 0x3;
                        }
                        break;

                    case 0x1:
                        // West
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x3;
                        } else {
                            direction = 0x2;
                        }
                        break;

                    case 0x2:
                        // South
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x1;
                        } else {
                            direction = 0x0;
                        }
                        break;

                    case 0x3:
                        // North
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x0;
                        } else {
                            direction = 0x1;
                        }
                        break;
                }
                data = (byte) ((data & 0x4) | direction);
                return data;

            case 63:

                constant = 4;

                if (rotation == Rotation.ANTICLOCKWISE) {
                    constant = -4;
                }

                data = (byte) (MathUtils.positiveMod((data + constant) % 16, 16));

                return data;

            case 64:
            case 71:
            case 193:
            case 194:
            case 195:
            case 196:
            case 197:
                boolean isRealDoor = (data & 0x8) == 0;

                if (isRealDoor) {
                    direction = (byte) (data & 0x3);
                    int newDirection;
                    if (rotation == Rotation.CLOCKWISE)
                        newDirection = direction + 1;
                    else
                        newDirection = direction - 1;
                    if (newDirection == 4)
                        newDirection = 0;
                    if (newDirection == -1)
                        newDirection = 3;

                    data = (byte) ((data & 0xC) | newDirection);
                }

                return data;

            case 66:
                direction = (byte) (data & 0x5);
                boolean flat = direction == 0x0 || direction == 0x1;

                if (flat) {

                    constant = 1;

                    if (rotation == Rotation.ANTICLOCKWISE) {
                        constant = -1;
                    }

                    direction = (byte) (MathUtils.positiveMod((direction + constant) % 2, 2));

                    data = direction;

                } else {

                    if (data >= 0x6) {
                        // Is a corner piece
                        constant = 1;

                        if (rotation == Rotation.ANTICLOCKWISE) {
                            constant = -1;
                        }

                        direction = (byte) (MathUtils.positiveMod(((data >> 4) + constant) % 4, 4));

                        data = (byte) (direction << 4);

                    } else {
                        // Is a rising piece
                        switch (direction) {
                            case 0x2:
                                // East
                                if (rotation == Rotation.CLOCKWISE) {
                                    direction = 0x4;
                                } else {
                                    direction = 0x5;
                                }
                                break;

                            case 0x3:
                                // West
                                if (rotation == Rotation.CLOCKWISE) {
                                    direction = 0x5;
                                } else {
                                    direction = 0x4;
                                }
                                break;

                            case 0x4:
                                // South
                                if (rotation == Rotation.CLOCKWISE) {
                                    direction = 0x3;
                                } else {
                                    direction = 0x2;
                                }
                                break;

                            case 0x5:
                                // North
                                if (rotation == Rotation.CLOCKWISE) {
                                    direction = 0x2;
                                } else {
                                    direction = 0x3;
                                }
                                break;
                        }

                        data = direction;
                    }

                }

                return data;


            case 27:
            case 28:
            case 157:
                direction = (byte) (data & 0x5);
                flat = direction == 0x0 || direction == 0x1;

                if (flat) {

                    constant = 1;

                    if (rotation == Rotation.ANTICLOCKWISE) {
                        constant = -1;
                    }

                    direction = (byte) (MathUtils.positiveMod((direction + constant) % 2, 2));

                    data = direction;

                } else {

                    // Is a rising piece
                    switch (direction) {
                        case 0x2:
                            // East
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x4;
                            } else {
                                direction = 0x5;
                            }
                            break;

                        case 0x3:
                            // West
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x5;
                            } else {
                                direction = 0x4;
                            }
                            break;

                        case 0x4:
                            // South
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x3;
                            } else {
                                direction = 0x2;
                            }
                            break;

                        case 0x5:
                            // North
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x2;
                            } else {
                                direction = 0x3;
                            }
                            break;
                    }

                    data = direction;


                }

                return data;

            case 65:
            case 68:
            case 61:
            case 62:
            case 23:
            case 130:
            case 154:
            case 158:
                if (data == 0x0 || data == 0x1) {
                    return data;
                }

                direction = (byte) (data & 0x7);
                switch (direction) {
                    case 0x5:
                        // East
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x3;
                        } else {
                            direction = 0x2;
                        }
                        break;

                    case 0x4:
                        // West
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x2;
                        } else {
                            direction = 0x3;
                        }
                        break;

                    case 0x3:
                        // South
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x4;
                        } else {
                            direction = 0x5;
                        }
                        break;

                    case 0x2:
                        // North
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x5;
                        } else {
                            direction = 0x4;
                        }
                        break;
                }

                data = direction;
                return data;

            case 69:
                direction = (byte) (data & 0x7);
                if (direction >= 0x1 && direction <= 0x4) {

                    switch (direction) {
                        case 0x1:
                            // East
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x3;
                            } else {
                                direction = 0x4;
                            }
                            break;

                        case 0x2:
                            // West
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x4;
                            } else {
                                direction = 0x3;
                            }
                            break;

                        case 0x3:
                            // South
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x2;
                            } else {
                                direction = 0x1;
                            }
                            break;

                        case 0x4:
                            // North
                            if (rotation == Rotation.CLOCKWISE) {
                                direction = 0x1;
                            } else {
                                direction = 0x2;
                            }
                            break;
                    }

                    data = (byte) ((data & 0x8) | direction);

                } else {
                    switch (direction) {
                        case 0x5:
                            data = (byte) ((data & 0x8) | 0x6);
                            break;
                        case 0x6:
                            data = (byte) ((data & 0x8) | 0x5);
                            break;
                        case 0x7:
                            data = (byte) ((data & 0x8));
                            break;
                        case 0x0:
                            data = (byte) ((data & 0x8) | 0x7);
                            break;
                    }
                }

                return data;

            case 77:
            case 143:
                direction = (byte) (data & 0x7);
                switch (direction) {
                    case 0x1:
                        // East
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x3;
                        } else {
                            direction = 0x4;
                        }
                        break;

                    case 0x2:
                        // West
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x4;
                        } else {
                            direction = 0x3;
                        }
                        break;

                    case 0x3:
                        // South
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x2;
                        } else {
                            direction = 0x1;
                        }
                        break;

                    case 0x4:
                        // North
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x1;
                        } else {
                            direction = 0x2;
                        }
                        break;
                }

                data = (byte) ((data & 0x8) | direction);

                return data;

            case 86:
            case 91:
                direction = (byte) (data & 0x3);

                if (data == 0x4) {
                    data = 0x4;
                } else {
                    constant = 1;

                    if (rotation == Rotation.ANTICLOCKWISE) {
                        constant = -1;
                    }

                    direction = (byte) (MathUtils.positiveMod((direction + constant) % 4, 4));

                    data = direction;
                }

                return data;

            case 93:
            case 94:
                direction = (byte) (data & 0x3);

                constant = 1;

                if (rotation == Rotation.ANTICLOCKWISE) {
                    constant = -1;
                }

                direction = (byte) (MathUtils.positiveMod((direction + constant) % 4, 4));

                data = (byte) ((data & 0xC) | direction);

                return data;

            case 96:
            case 167:
                direction = (byte) (data & 0x3);
                switch (direction) {
                    case 0x2:
                        // East
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x0;
                        } else {
                            direction = 0x1;
                        }
                        break;

                    case 0x3:
                        // West
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x1;
                        } else {
                            direction = 0x0;
                        }
                        break;

                    case 0x0:
                        // South
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x3;
                        } else {
                            direction = 0x2;
                        }
                        break;

                    case 0x1:
                        // North
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x2;
                        } else {
                            direction = 0x3;
                        }
                        break;
                }

                data = (byte) ((data & 0xfc) | direction);

                return data;
            case 106:
                if (data != 0x0) {
                    if (rotation == Rotation.CLOCKWISE) {
                        data = (byte) (data << 1);
                    } else {
                        data = (byte) (data >> 1);
                    }

                    if (data > 8) {
                        data = 1;
                    } else if (data == 0x0) {
                        data = 8;
                    }

                    return data;
                } else {
                    return data;
                }

            case 107:
            case 183:
            case 184:
            case 185:
            case 186:
            case 187:
            case 120:
                direction = (byte) (data & 0x3);

                constant = 1;

                if (rotation == Rotation.ANTICLOCKWISE) {
                    constant = -1;
                }

                direction = (byte) (MathUtils.positiveMod((direction + constant) % 4, 4));

                data = (byte) ((data & 0x4) | direction);

                return data;

            case 131:
                direction = (byte) (data & 0x3);

                constant = 1;

                if (rotation == Rotation.ANTICLOCKWISE) {
                    constant = -1;
                }

                direction = (byte) (MathUtils.positiveMod((direction + constant) % 4, 4));

                data = (byte) ((data & 0xC) | direction);

                return data;

            case 144:
                direction = data;
                switch (direction) {
                    case 0x1:
                        return data;
                    case 0x4:
                        // East
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x3;
                        } else {
                            direction = 0x2;
                        }
                        break;

                    case 0x5:
                        // West
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x2;
                        } else {
                            direction = 0x3;
                        }
                        break;

                    case 0x3:
                        // South
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x5;
                        } else {
                            direction = 0x4;
                        }
                        break;

                    case 0x2:
                        // North
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x4;
                        } else {
                            direction = 0x5;
                        }
                        break;
                }

                return direction;

            case 145:
                direction = (byte) (data & 0x1);
                constant = 1;

                if (rotation == Rotation.ANTICLOCKWISE) {
                    constant = -1;
                }

                direction = (byte) (MathUtils.positiveMod((direction + constant) % 2, 2));

                data = (byte) ((data & 0xC) | direction);

                return data;

            case 99:
            case 100:
                direction = data;
                switch (direction) {
                    case 0x0:
                    case 0x5:
                    case 0xA:
                    case 0xE:
                    case 0xF:
                        return data;
                    case 0x2:
                        //North
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x6;
                        } else {
                            direction = 0x4;
                        }
                        break;
                    case 0x4:
                        //East
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x2;
                        } else {
                            direction = 0x8;
                        }
                        break;
                    case 0x6:
                        //West
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x8;
                        } else {
                            direction = 0x2;
                        }
                        break;
                    case 0x8:
                        //South
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x4;
                        } else {
                            direction = 0x6;
                        }
                        break;
                    case 0x1:
                        //North and West
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x3;
                        } else {
                            direction = 0x7;
                        }
                        break;
                    case 0x3:
                        //North and East
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x9;
                        } else {
                            direction = 0x1;
                        }
                        break;
                    case 0x7:
                        //South and West
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x1;
                        } else {
                            direction = 0x9;
                        }
                        break;
                    case 0x9:
                        //South and East
                        if (rotation == Rotation.CLOCKWISE) {
                            direction = 0x7;
                        } else {
                            direction = 0x3;
                        }
                        break;
                }

                return direction;

            default:
                return data;
        }
    }
}