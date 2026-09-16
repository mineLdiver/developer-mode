package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.EntityPreview;
import net.mine_diver.developermode.client.gui.TextField;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.client.summon.SummonMode;
import net.mine_diver.developermode.feature.entity.EntitySummoning;
import net.mine_diver.developermode.feature.entity.SummonPresets;
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
 * Every registered entity type, with a live model beside each name. Left click
 * arms {@link SummonMode} with a plain one and hands you back to the world to
 * place it; right click opens {@link SummonPresetWindow} instead, to set up the
 * NBT it will be born with before there is anything to edit.
 */
public final class SummonWindow extends DevWindow {
    private static final int ROW_HEIGHT = 22;
    private static final int ICON_WIDTH = 20;
    private static final int GAP = 3;
    private static final int FOOTER_HEIGHT = 21;
    /** Marks a type that has saved presets waiting behind a right click. */
    private static final String PRESET_MARKER = "nbt";

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
        super("Summon", 168, 200);
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

            Entity preview = previewOf(minecraft, type);
            if (preview != null && !unrenderable.contains(type)) {
                if (!EntityPreview.render(minecraft, preview, contentX() + 1, rowY + 1, ICON_WIDTH, ROW_HEIGHT - 2, 0,
                        hovered ? Theme.ACCENT : EntityPreview.UNTINTED)) {
                    unrenderable.add(type);
                }
            }

            // Saved presets are otherwise invisible until you right click
            // every type in turn to find out which of them has any.
            int markerWidth = 0;
            if (SummonPresets.any(type)) {
                markerWidth = Draw.textWidth(minecraft, PRESET_MARKER) + 4;
                Draw.text(minecraft, PRESET_MARKER,
                        contentX() + contentWidth() - markerWidth, rowY + 7, Theme.ACCENT);
            }

            boolean broken = preview == null || unrenderable.contains(type);
            int ink = broken ? Theme.TEXT_FAINT : hovered ? Theme.ACCENT : Theme.TEXT;
            Draw.text(minecraft, Draw.ellipsize(minecraft, type, contentWidth() - ICON_WIDTH - 8 - markerWidth),
                    contentX() + ICON_WIDTH + 5, rowY + 7, ink);

            // Over the row's own contents, not under them. A wash laid down
            // first is painted over by the model that follows it, which leaves
            // the hovered row as the one row whose model is not tinted, and an
            // untinted model in a lit band reads as the neighbor being picked.
            if (hovered) {
                Draw.rect(contentX(), rowY, contentX() + contentWidth(), rowY + ROW_HEIGHT, Theme.HOVER);
            }
        }

        renderScrollbar(listTop, listHeight, visible);

        int footerY = contentY() + contentHeight() - FOOTER_HEIGHT + 1;
        Draw.text(minecraft, Draw.ellipsize(minecraft, status.isEmpty() ? "left click summons a plain one" : status, contentWidth()),
                contentX(), footerY, status.isEmpty() ? Theme.TEXT_FAINT : Theme.DANGER);
        Draw.text(minecraft, "right click sets up its NBT", contentX(), footerY + 10, Theme.TEXT_FAINT);
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
        if (button == 1) {
            // Allowed even for types the list has grayed out: seeing what a
            // broken one is actually made of is half of finding out why.
            SummonPresetWindow.open(type);
            return;
        }

        // Anything whose icon could not be drawn would crash the world render
        // every frame once spawned, and there is no recovering from that
        // without quitting. Refuse it here, where a message is still possible.
        if (unrenderable.contains(type) || previewOf(DeveloperModeClient.minecraft(), type) == null) {
            status = type + " cannot be built without more setup";
            return;
        }

        status = "";
        SummonMode.arm(type, null);
        DeveloperModeClient.minecraft().setScreen(null);
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
        // Plain, with nothing loaded into it, because that is what left
        // clicking the row summons. A preset is previewed where it is edited.
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
