package net.mine_diver.developermode.feature.world.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A tuned stack written out for a mod's source, since transforms exist only
 * in developer-mode and the result has to end up somewhere that ships.
 *
 * <p>As code, it is one class with nothing of developer-mode in it: the mod
 * hands it its generator's density, and gets the density with the transforms
 * laid over it. Each kind of transform is written from a template of its own
 * that does exactly what the transform does here, in the same order and with
 * every number written to the last digit, so the mod's generator built from
 * it can be compared with the preview on the same seed, and any difference is
 * a slip in the port.
 *
 * <p>As a recipe, it is the same stack in words, for writing down or reading
 * back by hand.
 */
public final class Export {
    private Export() {}

    /**
     * The stack in words, a transform to a line.
     *
     * @param names gives a translation key's text
     */
    public static String recipe(Stack stack, Function<String, String> names) {
        StringBuilder recipe = new StringBuilder();
        for (Transform transform : stack.transforms()) {
            recipe.append(names.apply(transform.key()));
            if (!transform.enabled()) recipe.append(" (").append(names.apply("gui.developermode.transforms.off")).append(')');
            recipe.append(": ");
            List<String> values = new ArrayList<>();
            for (Parameter parameter : transform.parameters())
                values.add(names.apply(parameter.key()) + " " + number(parameter));
            recipe.append(String.join(", ", values)).append('\n');
        }
        return recipe.toString();
    }

    /** The stack as a Java class, laying every transform that is switched on over a density, in order. */
    public static String code(Stack stack, Function<String, String> names) {
        StringBuilder code = new StringBuilder();
        code.append("import java.util.HashMap;\n");
        code.append("import java.util.Map;\n");
        code.append("import java.util.Random;\n\n");
        code.append("/**\n");
        code.append(" * Terrain transforms tuned in developer-mode, as plain Java that needs\n");
        code.append(" * nothing else.\n");
        code.append(" *\n");
        for (String line : recipe(stack, names).split("\n"))
            if (!line.isEmpty()) code.append(" * <p>").append(line).append('\n');
        code.append(" */\n");
        code.append("public final class TerrainTransforms {\n");
        code.append("    private TerrainTransforms() {}\n\n");
        code.append("    /** A density at any block. Above zero is solid. */\n");
        code.append("    @FunctionalInterface\n");
        code.append("    public interface Density {\n");
        code.append("        double at(int x, int y, int z);\n");
        code.append("    }\n\n");
        code.append("    /** Your generator's density with the transforms laid over it, in the order they were stacked. */\n");
        code.append("    public static Density over(Density noise, long seed) {\n");
        code.append("        Density stage = noise;\n");

        StringBuilder methods = new StringBuilder();
        boolean warps = false;
        int number = 0;
        for (Transform transform : stack.transforms()) {
            if (!transform.enabled()) continue;
            number++;
            String name = transform.kind() + number;
            if (transform instanceof VortexWarp) {
                code.append("        stage = ").append(name).append("(stage, seed);\n");
                methods.append(vortex(name, transform));
                warps = true;
            } else if (transform instanceof Threshold) {
                code.append("        stage = ").append(name).append("(stage);\n");
                methods.append(threshold(name, transform));
            }
        }
        code.append("        return stage;\n");
        code.append("    }\n");
        code.append(methods);
        if (warps) code.append(BETWEEN);
        code.append("}\n");
        return code.toString();
    }

    private static String threshold(String name, Transform transform) {
        return "\n"
                + "    /** Fills only above " + number(transform.parameters().get(0)) + ". */\n"
                + "    private static Density " + name + "(Density below) {\n"
                + "        return (x, y, z) -> below.at(x, y, z) - " + number(transform.parameters().get(0)) + ";\n"
                + "    }\n";
    }

    private static String vortex(String name, Transform transform) {
        List<Parameter> p = transform.parameters();
        return "\n"
                + "    /** Twists the density into vortexes, one in some of the square cells the world is divided into. */\n"
                + "    private static Density " + name + "(Density below, long seed) {\n"
                + "        int cell = " + number(p.get(0)) + ";\n"
                + "        double chance = " + number(p.get(1)) + ";\n"
                + "        double radius = Math.min(" + number(p.get(2)) + ", cell);\n"
                + "        double twist = " + number(p.get(3)) + " * Math.PI * 2;\n"
                + "        double falloff = " + number(p.get(4)) + ";\n"
                + "        double height = " + number(p.get(5)) + ";\n"
                + "        double verticalReach = " + number(p.get(6)) + ";\n"
                + "        double strength = " + number(p.get(7)) + ";\n"
                + "        Map<Long, double[]> centers = new HashMap<>();\n"
                + "        return (x, y, z) -> {\n"
                + "            double fromX = x + 0.5;\n"
                + "            double fromZ = z + 0.5;\n"
                + "            double vertical = 1 - Math.abs(y - height) / verticalReach;\n"
                + "            if (vertical > 0 && strength > 0) {\n"
                + "                int cellX = (int) Math.floor((x + 0.5) / cell);\n"
                + "                int cellZ = (int) Math.floor((z + 0.5) / cell);\n"
                + "                for (int dz = -1; dz <= 1; dz++) {\n"
                + "                    for (int dx = -1; dx <= 1; dx++) {\n"
                + "                        int atX = cellX + dx;\n"
                + "                        int atZ = cellZ + dz;\n"
                + "                        double[] center = centers.computeIfAbsent((long) atX << 32 | atZ & 0xFFFFFFFFL, key -> {\n"
                + "                            Random random = new Random(seed ^ (atX * 341873128712L + atZ * 132897987541L));\n"
                + "                            if (random.nextDouble() >= chance) return new double[0];\n"
                + "                            return new double[] { (atX + random.nextDouble()) * cell, (atZ + random.nextDouble()) * cell };\n"
                + "                        });\n"
                + "                        if (center.length == 0) continue;\n"
                + "                        double offsetX = fromX - center[0];\n"
                + "                        double offsetZ = fromZ - center[1];\n"
                + "                        double distance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);\n"
                + "                        if (distance >= radius) continue;\n"
                + "                        double angle = twist * strength * vertical * Math.pow(1 - distance / radius, falloff);\n"
                + "                        double cos = Math.cos(angle);\n"
                + "                        double sin = Math.sin(angle);\n"
                + "                        fromX = center[0] + offsetX * cos - offsetZ * sin;\n"
                + "                        fromZ = center[1] + offsetX * sin + offsetZ * cos;\n"
                + "                    }\n"
                + "                }\n"
                + "            }\n"
                + "            if (fromX == x + 0.5 && fromZ == z + 0.5) return below.at(x, y, z);\n"
                + "            return between(below, fromX - 0.5, y, fromZ - 0.5);\n"
                + "        };\n"
                + "    }\n";
    }

    /** The same blend {@link Sampler#between} does, written out for code that has no sampler. */
    private static final String BETWEEN = "\n"
            + "    /** A density between blocks, blended from the eight around the point. */\n"
            + "    private static double between(Density density, double x, double y, double z) {\n"
            + "        int x0 = (int) Math.floor(x);\n"
            + "        int y0 = (int) Math.floor(y);\n"
            + "        int z0 = (int) Math.floor(z);\n"
            + "        double fx = x - x0;\n"
            + "        double fy = y - y0;\n"
            + "        double fz = z - z0;\n"
            + "        double value = 0;\n"
            + "        for (int corner = 0; corner < 8; corner++) {\n"
            + "            int dx = corner & 1;\n"
            + "            int dy = corner >> 1 & 1;\n"
            + "            int dz = corner >> 2;\n"
            + "            double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);\n"
            + "            if (weight == 0) continue;\n"
            + "            value += weight * density.at(x0 + dx, y0 + dy, z0 + dz);\n"
            + "        }\n"
            + "        return value;\n"
            + "    }\n";

    /** A number as Java writes it back exactly, and a whole one without a point. */
    private static String number(Parameter parameter) {
        return parameter.whole() ? Long.toString(Math.round(parameter.get())) : Double.toString(parameter.get());
    }
}
