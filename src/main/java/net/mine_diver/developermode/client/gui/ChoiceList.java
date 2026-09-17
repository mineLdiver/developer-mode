package net.mine_diver.developermode.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A short list of what a field is allowed to hold, typed into to narrow it.
 *
 * <p>Drawn over the window rather than inside it, so it is not cut off by the
 * tree it belongs to, and kept inside the window's own edges so the desktop
 * still hands it the clicks: a window only receives what lands on it.
 *
 * <p>There are hundreds of items and six rows, so the search is the interface
 * and the rows are the confirmation.
 */
public final class ChoiceList {
    public static final int ROW_HEIGHT = 10;

    private static final int VISIBLE_ROWS = 6;
    private static final int WIDTH = 164;
    private static final int PADDING = 2;
    /** An item is drawn at sixteen, and a row is not that tall. */
    private static final int ICON_SIZE = ROW_HEIGHT - 1;

    private final TextField search = new TextField(32);
    private final List<NbtShape.Choice> all = new ArrayList<>();
    private final List<NbtShape.Choice> matches = new ArrayList<>();

    private Consumer<NbtShape.Choice> onPick;
    private boolean open;
    private int x;
    private int y;
    private int scrollRow;

    public boolean isOpen() {
        return open;
    }

    /**
     * Shows the list near a row, nudged to stay inside the given bounds.
     *
     * @param onPick handed the value of whatever is chosen
     */
    public void open(List<NbtShape.Choice> choices, int nearX, int nearY,
                     int boundsX, int boundsY, int boundsWidth, int boundsHeight,
                     Consumer<NbtShape.Choice> onPick) {
        all.clear();
        all.addAll(choices);
        this.onPick = onPick;

        search.setText("");
        search.setFocused(true);
        scrollRow = 0;
        applyFilter();

        x = Math.max(boundsX, Math.min(nearX, boundsX + boundsWidth - WIDTH));
        y = Math.max(boundsY, Math.min(nearY, boundsY + boundsHeight - height()));
        open = true;
    }

    public void close() {
        open = false;
        onPick = null;
        all.clear();
        matches.clear();
        search.setFocused(false);
    }

    public void tick() {
        if (open) search.tick();
    }

    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        if (!open) return;

        Draw.rect(x, y, x + WIDTH, y + height(), Theme.PANEL_RAISED);
        Draw.outline(x, y, WIDTH, height(), Theme.BORDER_FOCUSED);

        search.bounds(x + PADDING, y + PADDING, WIDTH - PADDING * 2);
        search.render(minecraft, "Search " + all.size());

        int listTop = y + PADDING + TextField.HEIGHT + PADDING;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            if (index >= matches.size()) break;

            int rowY = listTop + i * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            NbtShape.Choice choice = matches.get(index);
            int labelX = x + PADDING + 1;
            ItemStack icon = choice.icon();
            if (icon != null) {
                ItemDraw.scaled(minecraft, icon, labelX, rowY, ICON_SIZE);
                labelX += ICON_SIZE + 2;
            }

            Draw.text(minecraft,
                    Draw.ellipsize(minecraft, choice.label(), x + WIDTH - PADDING - labelX),
                    labelX, rowY + 1, hovered ? Theme.ACCENT : Theme.TEXT);

            // Over the row's own contents, so the icon is tinted with it.
            if (hovered) Draw.rect(x + 1, rowY, x + WIDTH - 1, rowY + ROW_HEIGHT, Theme.HOVER);
        }
    }

    /** @return true if the click belonged to the list rather than what is under it */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!open) return false;

        if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + height()) {
            close();
            return true;
        }
        if (search.contains(mouseX, mouseY)) {
            search.setFocused(true);
            return true;
        }

        int listTop = y + PADDING + TextField.HEIGHT + PADDING;
        int row = (mouseY - listTop) / ROW_HEIGHT;
        if (mouseY >= listTop && row >= 0 && row < VISIBLE_ROWS) {
            int index = scrollRow + row;
            if (index < matches.size()) pick(matches.get(index));
        }
        return true;
    }

    public boolean mouseScrolled(int direction) {
        if (!open) return false;
        scrollRow = Math.max(0, Math.min(scrollRow - direction, Math.max(0, matches.size() - VISIBLE_ROWS)));
        return true;
    }

    public boolean keyPressed(char character, int keyCode) {
        if (!open) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            if (!matches.isEmpty()) pick(matches.get(Math.min(scrollRow, matches.size() - 1)));
            return true;
        }
        if (search.keyPressed(character, keyCode)) {
            scrollRow = 0;
            applyFilter();
        }
        return true;
    }

    private void pick(NbtShape.Choice choice) {
        Consumer<NbtShape.Choice> picked = onPick;
        close();
        if (picked != null) picked.accept(choice);
    }

    private void applyFilter() {
        String query = search.text().trim().toLowerCase(Locale.ROOT);
        matches.clear();
        for (NbtShape.Choice choice : all) {
            if (query.isEmpty() || describes(choice).contains(query)) matches.add(choice);
        }
    }

    /** Label and stored values together, so either one finds a choice. */
    private static String describes(NbtShape.Choice choice) {
        StringBuilder text = new StringBuilder(choice.label());
        for (String written : choice.writes().values()) text.append(' ').append(written);
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private int height() {
        return PADDING + TextField.HEIGHT + PADDING + VISIBLE_ROWS * ROW_HEIGHT + PADDING;
    }
}
