package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.world.transform.Parameter;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

import java.util.Locale;

/**
 * A bar for setting a number by dragging along it, with its name and value
 * written on it.
 */
public final class Slider {
    public static final int HEIGHT = 11;

    private final Parameter parameter;
    public int x;
    public int y;
    public int width;
    private boolean dragging;

    public Slider(Parameter parameter) {
        this.parameter = parameter;
    }

    public void bounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + HEIGHT;
    }

    /** @return whether the press was on this, which starts a drag */
    public boolean press(int pointX, int pointY) {
        if (!contains(pointX, pointY)) return false;
        dragging = true;
        follow(pointX);
        return true;
    }

    /** Called every frame. Follows the pointer for as long as the button is held. */
    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        if (dragging) {
            if (!Mouse.isButtonDown(0)) dragging = false;
            else follow(mouseX);
        }

        boolean lit = dragging || contains(mouseX, mouseY);
        Draw.rect(x, y, x + width, y + HEIGHT, Theme.PANEL_SUNKEN);
        double along = (parameter.get() - parameter.min()) / (parameter.max() - parameter.min());
        Draw.rect(x, y, x + Math.round(width * along), y + HEIGHT, Theme.ACCENT_FILL);
        Draw.outline(x, y, width, HEIGHT, lit ? Theme.BORDER_FOCUSED : Theme.BORDER);

        Draw.text(minecraft, Draw.ellipsize(minecraft, Lang.get(parameter.key()), width - 40), x + 3, y + 2, Theme.TEXT);
        String value = parameter.whole()
                ? Long.toString(Math.round(parameter.get()))
                : String.format(Locale.ROOT, "%.2f", parameter.get());
        Draw.text(minecraft, value, x + width - Draw.textWidth(minecraft, value) - 3, y + 2, Theme.TEXT_DIM);
    }

    private void follow(int pointX) {
        if (pointX == DevWindow.POINTER_AWAY) return;
        double along = Math.max(0, Math.min(1, (pointX - x) / (double) Math.max(1, width - 1)));
        parameter.set(parameter.min() + (parameter.max() - parameter.min()) * along);
    }
}
