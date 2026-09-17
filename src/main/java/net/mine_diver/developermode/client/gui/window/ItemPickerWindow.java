package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.ItemCatalogue;
import net.mine_diver.developermode.client.gui.ItemDraw;
import net.mine_diver.developermode.client.gui.TextField;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.Give;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything in the item registry, in a searchable grid. Clicking gives.
 *
 * <p>Blocks are items in Beta, so this one window covers both.
 */
public final class ItemPickerWindow extends DevWindow {
    private static final int SLOT = 18;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final int GAP = 3;
    private static final int FOOTER_HEIGHT = 10;
    private static final int COLUMNS = 9;
    /** Beta stores block variants in four metadata bits, so this covers them all. */
    private static final int STATUS_DURATION_TICKS = 60;

    private final List<Entry> catalogue = new ArrayList<>();
    private final List<Entry> matches = new ArrayList<>();
    private final TextField search = new TextField(48);

    private int scrollRow;
    private Entry hovered;
    private int hoveredX;
    private int hoveredY;
    private String status = "";
    private int statusTicks;
    private int statusSequence = DevStatus.sequence(DevStatus.GIVE);

    public ItemPickerWindow() {
        super("Items", COLUMNS * SLOT + PADDING * 2 + SCROLLBAR_WIDTH + 2, 178);
        buildCatalogue();
        applyFilter();
        search.setFocused(true);
    }

    @Override
    public void tick() {
        search.tick();
        if (statusTicks > 0 && --statusTicks == 0) status = "";

        // Giving is a request, and the answer comes back as a packet long after
        // the click that asked for it has returned.
        if (DevStatus.sequence(DevStatus.GIVE) != statusSequence) {
            statusSequence = DevStatus.sequence(DevStatus.GIVE);
            setStatus(DevStatus.message(DevStatus.GIVE));
        }
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        search.bounds(contentX(), contentY(), contentWidth());
        search.render(minecraft, "Search " + catalogue.size() + " items");

        hovered = null;
        int columns = columns();
        int rows = rows();
        clampScroll();

        ItemDraw.begin();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = (scrollRow + row) * columns + column;
                if (index >= matches.size()) break;

                Entry entry = matches.get(index);
                int slotX = gridX() + column * SLOT;
                int slotY = gridY() + row * SLOT;

                if (mouseX >= slotX && mouseX < slotX + SLOT && mouseY >= slotY && mouseY < slotY + SLOT) {
                    hovered = entry;
                    hoveredX = slotX;
                    hoveredY = slotY;
                }

                ItemDraw.stack(minecraft, entry.stack, slotX + 1, slotY + 1, false);
            }
        }
        ItemDraw.end();

        if (hovered != null) {
            Draw.rect(hoveredX, hoveredY, hoveredX + SLOT, hoveredY + SLOT, Theme.HOVER);
        }

        renderScrollbar();
        renderFooter(minecraft);
    }

    @Override
    public void renderOverlay(Minecraft minecraft, int mouseX, int mouseY) {
        if (hovered == null) return;

        String name = hovered.name;
        String subtitle = hovered.id;
        int textWidth = Math.max(Draw.textWidth(minecraft, name), Draw.textWidth(minecraft, subtitle));

        int tooltipX = mouseX + 9;
        int tooltipY = mouseY - 6;
        Draw.rect(tooltipX - 3, tooltipY - 3, tooltipX + textWidth + 3, tooltipY + 19, Theme.PANEL_RAISED);
        Draw.outline(tooltipX - 3, tooltipY - 3, textWidth + 6, 22, Theme.BORDER);
        Draw.text(minecraft, name, tooltipX, tooltipY, Theme.TEXT);
        Draw.text(minecraft, subtitle, tooltipX, tooltipY + 10, Theme.TEXT_FAINT);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (search.contains(mouseX, mouseY)) {
            search.setFocused(true);
            return;
        }

        if (hovered == null) {
            search.setFocused(false);
            return;
        }

        ItemStack stack = hovered.stack.copy();
        stack.count = button == 1 ? 1 : Math.max(1, stack.getItem().getMaxCount());

        Give.give(stack);
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        scrollRow -= direction * 3;
        clampScroll();
    }

    @Override
    public void keyPressed(char character, int keyCode) {
        if (search.keyPressed(character, keyCode)) applyFilter();
    }

    @Override
    public boolean clearTypingFocus() {
        if (!search.isFocused()) return false;
        search.setFocused(false);
        return true;
    }

    private void renderScrollbar() {
        int totalRows = totalRows();
        int rows = rows();
        if (totalRows <= rows) return;

        int trackX = contentX() + contentWidth() - SCROLLBAR_WIDTH;
        int trackTop = gridY();
        int trackHeight = rows * SLOT;
        Draw.rect(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, Theme.PANEL_SUNKEN);

        int thumbHeight = Math.max(8, trackHeight * rows / totalRows);
        int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollRow / (totalRows - rows);
        Draw.rect(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, Theme.BORDER_FOCUSED);
    }

    private void renderFooter(Minecraft minecraft) {
        int footerY = contentY() + contentHeight() - FOOTER_HEIGHT + 1;
        if (!status.isEmpty()) {
            Draw.text(minecraft, Draw.ellipsize(minecraft, status, contentWidth()), contentX(), footerY, Theme.ACCENT);
            return;
        }
        Draw.text(minecraft, "left stack   right one", contentX(), footerY, Theme.TEXT_FAINT);
    }

    private void setStatus(String message) {
        status = message;
        statusTicks = STATUS_DURATION_TICKS;
    }

    private int gridX() {
        return contentX();
    }

    private int gridY() {
        return contentY() + TextField.HEIGHT + GAP;
    }

    private int gridHeight() {
        return contentHeight() - TextField.HEIGHT - GAP - FOOTER_HEIGHT;
    }

    private int columns() {
        return Math.max(1, (contentWidth() - SCROLLBAR_WIDTH - 1) / SLOT);
    }

    private int rows() {
        return Math.max(1, gridHeight() / SLOT);
    }

    private int totalRows() {
        int columns = columns();
        return (matches.size() + columns - 1) / columns;
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows() - rows())));
    }

    private void applyFilter() {
        String query = search.text().trim().toLowerCase(Locale.ROOT);
        matches.clear();
        if (query.isEmpty()) {
            matches.addAll(catalogue);
        } else {
            for (Entry entry : catalogue) {
                if (entry.searchKey.contains(query)) matches.add(entry);
            }
        }
        scrollRow = 0;
    }

    private void buildCatalogue() {
        for (ItemCatalogue.Variant variant : ItemCatalogue.all()) {
            catalogue.add(new Entry(variant.stack(), variant.name(), variant.displayId()));
        }
    }

    /**
     * Adds one stack unless an earlier metadata value already produced the
     * same name and sprite, which is how Beta items without real variants show
     * up sixteen times over.
     */
    private static final class Entry {
        final ItemStack stack;
        final String name;
        final String id;
        final String searchKey;

        Entry(ItemStack stack, String name, String id) {
            this.stack = stack;
            this.name = name;
            this.id = id;
            this.searchKey = (name + " " + id).toLowerCase(Locale.ROOT);
        }
    }
}
