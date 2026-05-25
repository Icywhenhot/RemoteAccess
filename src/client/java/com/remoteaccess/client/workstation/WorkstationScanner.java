package com.remoteaccess.client.workstation;

import com.remoteaccess.client.config.RemoteAccessConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cubic block search for nearby workstations. Run only on screen open / switch — never per frame.
 */
public final class WorkstationScanner {

    private WorkstationScanner() {}

    public static List<Workstation> scan(Level level, Player player, BlockPos center, RemoteAccessConfig config) {
        int radius = (int) Math.ceil(config.searchRadius);
        double maxDist = Math.min(config.searchRadius, config.reachLimit);
        double maxDistSq = maxDist * maxDist;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double lookYaw = Math.atan2(look.z, look.x);

        List<Workstation> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!WorkstationRegistry.isWorkstation(state, config)) {
                        continue;
                    }

                    double cx = cursor.getX() + 0.5;
                    double cy = cursor.getY() + 0.5;
                    double cz = cursor.getZ() + 0.5;
                    double distSq = eye.distanceToSqr(cx, cy, cz);
                    if (distSq > maxDistSq) {
                        continue;
                    }

                    double toYaw = Math.atan2(cz - eye.z, cx - eye.x);
                    double relAngle = Mth.wrapDegrees(Math.toDegrees(toYaw - lookYaw)) * Mth.DEG_TO_RAD;

                    found.add(new Workstation(cursor.immutable(), state.getBlock(), distSq, relAngle));
                }
            }
        }

        found.sort(comparator(config.sortMode));
        return found;
    }

    private static Comparator<Workstation> comparator(com.remoteaccess.client.config.SortMode mode) {
        return switch (mode) {
            case DISTANCE -> Comparator
                    .comparingDouble(Workstation::distSq)
                    .thenComparingDouble(Workstation::relAngle);
            case POSITION -> Comparator
                    .comparingInt((Workstation w) -> w.pos().getX())
                    .thenComparingInt(w -> w.pos().getY())
                    .thenComparingInt(w -> w.pos().getZ());
            case ANGULAR -> Comparator
                    .comparingDouble(Workstation::relAngle)
                    .thenComparingDouble(Workstation::distSq);
        };
    }
}
