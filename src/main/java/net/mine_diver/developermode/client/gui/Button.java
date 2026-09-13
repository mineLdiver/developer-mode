package net.mine_diver.developermode.client.gui;

import net.minecraft.client.Minecraft;

/**
 * A small flat button. Also does duty as a toggle, via {@link #toggled}.
 */
public final class Button {
    public static final int HEIGHT = 12;

    public String label;
    public boolean enabled = true;
    public boolean toggled;

    public int x;
    public int y;
    public int width;

    public Button(String label) {
        this.label = label;
    }

    public void bounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + HEIGHT;
    }

    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        boolean hovered = enabled && contains(mouseX, mouseY);

        int fill = !enabled ? Theme.PANEL_SUNKEN : toggled ? Theme.ACCENT_FILL : hovered ? Theme.PANEL_RAISED : Theme.PANEL_SUNKEN;
        int edge = !enabled ? Theme.BORDER : toggled || hovered ? Theme.BORDER_FOCUSED : Theme.BORDER;
        int ink = !enabled ? Theme.TEXT_FAINT : toggled ? Theme.ACCENT : Theme.TEXT;

        Draw.rect(x, y, x + width, y + HEIGHT, fill);
        Draw.outline(x, y, width, HEIGHT, edge);
        Draw.textCentered(minecraft, Draw.ellipsize(minecraft, label, width - 6), x + width / 2, y + 2, ink);
    }
}
