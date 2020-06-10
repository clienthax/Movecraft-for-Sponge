package io.github.pulverizer.movecraft.utils;

import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.tileentity.TileEntityArchetype;
import org.spongepowered.api.data.DataQuery;

import java.util.Optional;

public abstract class BlockSnapshotSignDataUtil {

    public static Optional<String> getTextLine(BlockSnapshot blockSnapshot, int lineNum) {
        Optional<TileEntityArchetype> archetype = blockSnapshot.createArchetype();

        if (!archetype.isPresent()) {
            return Optional.empty();
        }

        Optional<String> optional = archetype.get().getTileData().getString(DataQuery.of("Text" + lineNum));

        return optional.map(s -> s.split("\"")[3]);

    }
}
