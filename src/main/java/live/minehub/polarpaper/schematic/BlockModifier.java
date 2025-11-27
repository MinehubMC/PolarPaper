package live.minehub.polarpaper.schematic;

import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3i;

public interface BlockModifier {
    BlockState modify(Vector3i pos, BlockState blockState);
    void modify(Vector3i pos);
    void modifyEntity(Location location);

    class PosRot implements BlockModifier {

        private final Vector3i offset;
        private final Rotation rotation;
        public PosRot(Vector3i offset, Rotation rotation) {
            this.offset = offset;
            this.rotation = rotation;
        }

        public Vector3i getOffset() {
            return offset;
        }

        public Rotation getRotation() {
            return rotation;
        }

        @Override
        public BlockState modify(Vector3i pos, BlockState blockState) {
            if (rotation != Rotation.NONE) {
                blockState = blockState.rotate(rotation.getMcRot());
                Vector3d vec = new Vector3d(pos);
                rotatePos(vec, rotation);
                pos.x = (int) vec.x;
                pos.y = (int) vec.y;
                pos.z = (int) vec.z;
            }
            pos.add(offset);
            return blockState;
        }

        @Override
        public void modify(Vector3i pos) {
            if (rotation != Rotation.NONE) {
                Vector3d vec = new Vector3d(pos);
                rotatePos(vec, rotation);
                pos.x = (int) vec.x;
                pos.y = (int) vec.y;
                pos.z = (int) vec.z;
            }
            pos.add(offset);
        }

        @Override
        public void modifyEntity(Location pos) {
            if (rotation != Rotation.NONE) {
                Vector3d vec = new Vector3d(pos.x(), pos.y(), pos.z());
                rotatePos(vec, rotation);
                pos.setX(vec.x);
                pos.setY(vec.y);
                pos.setZ(vec.z);
                pos.setYaw(pos.getYaw() + rotation.toDegrees());

                switch (rotation) {
                    case CLOCKWISE_90 -> pos.add(1, 0, 0);
                    case CLOCKWISE_180 -> pos.add(1, 0, 1);
                    case CLOCKWISE_270 -> pos.add(0, 0, 1);
                }
            }
            pos.add(offset.x(), offset.y(), offset.z());
        }

        public static void rotatePos(@NotNull Vector3d point, @NotNull Rotation rotation) {
            switch (rotation) {
                case CLOCKWISE_90 -> {
                    double x = -point.z;
                    double z = point.x;
                    point.x = x;
                    point.z = z;
                }
                case CLOCKWISE_180 -> {
                    double x = -point.x;
                    double z = -point.z;
                    point.x = x;
                    point.z = z;
                }
                case CLOCKWISE_270 -> {
                    double x = point.z;
                    double z = -point.x;
                    point.x = x;
                    point.z = z;
                }
            }
        }
    }
}