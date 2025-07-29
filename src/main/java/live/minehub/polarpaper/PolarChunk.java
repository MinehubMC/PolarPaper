package live.minehub.polarpaper;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public record PolarChunk(
        int x,
        int z,
        PolarSection[] sections,
        List<BlockEntity> blockEntities,
        int[][] heightmaps,
        byte[] userData
) {

    public static final int HEIGHTMAP_NONE = 0b0;
    public static final int HEIGHTMAP_MOTION_BLOCKING = 0b1;
    public static final int HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = 0b10;
    public static final int HEIGHTMAP_OCEAN_FLOOR = 0b100;
    public static final int HEIGHTMAP_OCEAN_FLOOR_WG = 0b1000;
    public static final int HEIGHTMAP_WORLD_SURFACE = 0b10000;
    public static final int HEIGHTMAP_WORLD_SURFACE_WG = 0b100000;
    static final int[] HEIGHTMAPS = new int[]{
            HEIGHTMAP_NONE,
            HEIGHTMAP_MOTION_BLOCKING,
            HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES,
            HEIGHTMAP_OCEAN_FLOOR,
            HEIGHTMAP_OCEAN_FLOOR_WG,
            HEIGHTMAP_WORLD_SURFACE,
            HEIGHTMAP_WORLD_SURFACE_WG,
    };
    static final int HEIGHTMAP_SIZE = 16 * 16; // Chunk Size X * Chunk Size Z
    static final int MAX_HEIGHTMAPS = 32;

    public int @Nullable [] heightmap(int type) {
        return heightmaps[type];
    }

    public boolean isEmpty() {
        for (PolarSection section : sections) {
            if (!section.isEmpty()) return false;
        }
        return true;
    }

    public PolarChunk(int x, int z, int sectionCount) {
        // Blank chunk
        this(x, z, new PolarSection[sectionCount], List.of(), new int[PolarChunk.MAX_HEIGHTMAPS][0], new byte[0]);
        Arrays.setAll(sections, (i) -> new PolarSection());
    }

    public record BlockEntity(
            int index,
            @Nullable String id,
            @Nullable CompoundTag data
    ) {

    }

    public record Entity(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            byte[] bytes
    ) {

    }

}