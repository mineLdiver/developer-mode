package net.mine_diver.developermode.feature;

import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Casting a ray as far as it was asked to go.
 *
 * <p>Beta's own raycast gives up after two hundred block boundaries, and a ray
 * crosses up to a little under two of them per block depending on how squarely
 * it runs along an axis. Asked to look further than that it stops in mid air
 * and reports nothing, which reads as a reach much shorter than the one it was
 * given, and one that changes with the direction you face.
 *
 * <p>The limit is per call, so a long look is a run of short ones, each taking
 * over where the last gave out.
 */
public final class Ray {
    /**
     * Blocks per call. Short enough that no direction can spend the budget
     * inside one segment, with room to spare.
     */
    private static final double SEGMENT = 64;

    private Ray() {}

    /**
     * @param look unit vector along the ray
     * @return the first block face hit, or null if the ray ran out first
     */
    public static HitResult cast(World world, Vec3d origin, Vec3d look, double reach) {
        for (double from = 0; from < reach; from += SEGMENT) {
            double to = Math.min(from + SEGMENT, reach);
            HitResult hit = world.raycast(
                    origin.add(look.x * from, look.y * from, look.z * from),
                    origin.add(look.x * to, look.y * to, look.z * to));
            if (hit != null) return hit;
        }
        return null;
    }
}
