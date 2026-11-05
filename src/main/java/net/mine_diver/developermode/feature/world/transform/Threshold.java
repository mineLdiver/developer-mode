package net.mine_diver.developermode.feature.world.transform;

/**
 * Moves the line between solid and empty.
 *
 * <p>Generators fill wherever the density is above zero, so taking a number
 * off the density everywhere is the same as filling only above that number.
 * Raising it thins the land and widens every gap; lowering it does the
 * opposite.
 */
public final class Threshold extends Transform {
    public static final String KIND = "threshold";

    public Threshold() {
        super(KIND, "gui.developermode.transform.threshold",
                new Parameter("gui.developermode.transform.threshold.level", -200, 200, 0, false));
    }

    @Override
    public Sampler over(Sampler below, long seed) {
        double level = parameters().get(0).get();
        return (x, y, z) -> below.at(x, y, z) - level;
    }

    @Override
    public Transform copy() {
        return copyInto(new Threshold());
    }
}
