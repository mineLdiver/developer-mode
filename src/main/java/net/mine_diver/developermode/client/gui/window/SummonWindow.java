package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.EntityPreview;
import net.mine_diver.developermode.client.gui.TextField;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.client.summon.SummonMode;
import net.mine_diver.developermode.feature.entity.EntitySummoning;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every registered entity type, with a live model beside each name. Choosing
 * one arms {@link SummonMode} and hands you back to the world to place it.
 */
public final class SummonWindow extends DevWindow {
    private static final int ROW_HEIGHT = 22;
    private static final int ICON_WIDTH = 20;
    private static final int GAP = 3;
    private static final int FOOTER_HEIGHT = 10;

    private final List<String> types = new ArrayList<>();
    private final List<String> matches = new ArrayList<>();
    /** Built lazily: constructing every registered entity up front is wasteful. */
    private final Map<String, Entity> previews = new HashMap<>();
    private final TextField search = new TextField(32);
    /** Types whose renderer threw while drawing the row icon. */
    private final Set<String> unrenderable = new HashSet<>();

    private World previewWorld;
    private int scrollRow;
    private String status = "";

    public SummonWindow() {
        super("Summon", 168, 190);
        types.addAll(EntitySummoning.types());
        applyFilter();
        search.setFocused(true);
    }

    public static void open() {
        ComposerScreen.reveal(SummonWindow.class, SummonWindow::new);
    }

    @Override
    public void tick() {
        search.tick();
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        invalidatePreviewsOnWorldChange(minecraft);

        search.bounds(contentX(), contentY(), contentWidth());
        search.render(minecraft, "Search " + types.size() + " types");

        int listTop = contentY() + TextField.HEIGHT + GAP;
        int listHeight = contentHeight() - TextField.HEIGHT - GAP - FOOTER_HEIGHT;
        int visible = Math.max(1, listHeight / ROW_HEIGHT);
        clampScroll(visible);

        for (int i = 0; i < visible; i++) {
            int index = scrollRow + i;
            if (index >= matches.size()) break;

            String type = matches.get(index);
            int rowY = listTop + i * ROW_HEIGHT;
            boolean hovered = mouseX >= contentX() && mouseX < contentX() + contentWidth()
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                Draw.rect(contentX(), rowY, contentX() + contentWidth(), rowY + ROW_HEIGHT, Theme.HOVER);
            }

            Entity preview = previewOf(minecraft, type);
            if (preview != null && !unrenderable.contains(type)) {
                if (!EntityPreview.render(minecraft, preview, contentX() + 1, rowY + 1, ICON_WIDTH, ROW_HEIGHT - 2, 0)) {
                    unrenderable.add(type);
                }
            }

            boolean broken = preview == null || unrenderable.contains(type);
            int ink = broken ? Theme.TEXT_FAINT : hovered ? Theme.ACCENT : Theme.TEXT;
            Draw.text(minecraft, Draw.ellipsize(minecraft, type, contentWidth() - ICON_WIDTH - 8),
                    contentX() + ICON_WIDTH + 5, rowY + 7, ink);
        }

        renderScrollbar(listTop, listHeight, visible);

        int footerY = contentY() + contentHeight() - FOOTER_HEIGHT + 1;
        String footer = status.isEmpty() ? "click a type, then click in the world" : status;
        Draw.text(minecraft, Draw.ellipsize(minecraft, footer, contentWidth()), contentX(), footerY,
                status.isEmpty() ? Theme.TEXT_FAINT : Theme.DANGER);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (search.contains(mouseX, mouseY)) {
            search.setFocused(true);
            return;
        }
        search.setFocused(false);

        int listTop = contentY() + TextField.HEIGHT + GAP;
        int listHeight = contentHeight() - TextField.HEIGHT - GAP - FOOTER_HEIGHT;
        if (mouseY < listTop || mouseY >= listTop + listHeight) return;

        int index = scrollRow + (mouseY - listTop) / ROW_HEIGHT;
        if (index < 0 || index >= matches.size()) return;

        String type = matches.get(index);
        // Anything whose icon could not be drawn would crash the world render
        // every frame once spawned, and there is no recovering from that
        // without quitting. Refuse it here, where a message is still possible.
        if (unrenderable.contains(type) || previewOf(Minecraft.INSTANCE, type) == null) {
            status = type + " cannot be built without more setup";
            return;
        }

        status = "";
        SummonMode.arm(type);
        Minecraft.INSTANCE.setScreen(null);
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        scrollRow -= direction * 2;
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

    private Entity previewOf(Minecraft minecraft, String type) {
        if (previews.containsKey(type)) return previews.get(type);

        Entity preview = EntitySummoning.create(type, minecraft.world);
        if (preview != null) EntitySummoning.place(preview, 0, 0, 0, 0);
        previews.put(type, preview);
        return preview;
    }

    private void invalidatePreviewsOnWorldChange(Minecraft minecraft) {
        if (previewWorld == minecraft.world) return;
        previewWorld = minecraft.world;
        previews.clear();
        unrenderable.clear();
    }

    private void applyFilter() {
        String query = search.text().trim().toLowerCase(Locale.ROOT);
        matches.clear();
        for (String type : types) {
            if (query.isEmpty() || type.toLowerCase(Locale.ROOT).contains(query)) matches.add(type);
        }
        scrollRow = 0;
    }

    private void renderScrollbar(int listTop, int listHeight, int visible) {
        if (matches.size() <= visible) return;

        int trackX = contentX() + contentWidth() - 3;
        Draw.rect(trackX, listTop, trackX + 3, listTop + listHeight, Theme.PANEL_SUNKEN);

        int thumbHeight = Math.max(8, listHeight * visible / matches.size());
        int thumbTop = listTop + (listHeight - thumbHeight) * scrollRow / (matches.size() - visible);
        Draw.rect(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, Theme.BORDER_FOCUSED);
    }

    private void clampScroll(int visible) {
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, matches.size() - visible)));
    }
}
