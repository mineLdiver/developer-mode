package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Button;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.client.Freezing;
import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything loaded in the world, nearest first. Click one to edit it.
 *
 * <p>Aiming at something is the quick way in; this is the reliable one, for
 * whatever is behind a wall, inside a block, or too small to hit.
 */
public final class EntityListWindow extends DevWindow {
    private static final int ROW_HEIGHT = 11;
    private static final int FOOTER_HEIGHT = 15;
    /** Resorting by distance every tick would make the list squirm. */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private final List<Entity> entries = new ArrayList<>();
    private final Button thawAllButton = new Button("Release all");

    private int scrollRow;
    private int refreshCountdown;

    public EntityListWindow() {
        super("Entities", 176, 168);
        refresh();
    }

    public static void open() {
        ComposerScreen.reveal(EntityListWindow.class, EntityListWindow::new);
    }

    @Override
    public void tick() {
        if (--refreshCountdown <= 0) refresh();
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        int listHeight = contentHeight() - FOOTER_HEIGHT;
        int visible = Math.max(1, listHeight / ROW_HEIGHT);
        clampScroll(visible);

        Entity player = minecraft.player;

        for (int i = 0; i < visible; i++) {
            int index = scrollRow + i;
            if (index >= entries.size()) break;

            Entity entity = entries.get(index);
            int rowY = contentY() + i * ROW_HEIGHT;
            boolean hovered = mouseX >= contentX() && mouseX < contentX() + contentWidth()
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                Draw.rect(contentX(), rowY, contentX() + contentWidth(), rowY + ROW_HEIGHT, Theme.HOVER);
            }

            boolean frozen = FrozenEntities.isFrozen(entity);
            String distance = player == null ? "" : String.format("%.0fm", distanceBetween(player, entity));

            Draw.text(minecraft, Draw.ellipsize(minecraft, Entities.name(entity), contentWidth() - 46),
                    contentX() + 2, rowY + 2, frozen ? Theme.ACCENT : Theme.TEXT);
            Draw.text(minecraft, distance,
                    contentX() + contentWidth() - Draw.textWidth(minecraft, distance) - 5, rowY + 2, Theme.TEXT_FAINT);
        }

        if (entries.isEmpty()) {
            Draw.textCentered(minecraft, "nothing loaded", contentX() + contentWidth() / 2,
                    contentY() + 4, Theme.TEXT_FAINT);
        }

        renderScrollbar(visible, listHeight);

        int footerY = contentY() + contentHeight() - FOOTER_HEIGHT + 2;
        int frozenCount = FrozenEntities.frozenCount();
        thawAllButton.bounds(contentX(), footerY, 52);
        thawAllButton.enabled = frozenCount > 0;
        thawAllButton.render(minecraft, mouseX, mouseY);

        String summary = entries.size() + " loaded" + (frozenCount > 0 ? ", " + frozenCount + " frozen" : "");
        Draw.text(minecraft, summary, contentX() + 58, footerY + 2, Theme.TEXT_FAINT);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (thawAllButton.enabled && thawAllButton.contains(mouseX, mouseY)) {
            Freezing.releaseAll();
            return;
        }

        int listHeight = contentHeight() - FOOTER_HEIGHT;
        if (mouseY < contentY() || mouseY >= contentY() + listHeight) return;

        int index = scrollRow + (mouseY - contentY()) / ROW_HEIGHT;
        if (index < 0 || index >= entries.size()) return;

        Entity entity = entries.get(index);
        EntityEditorWindow.open(entity);
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        scrollRow -= direction * 3;
    }

    private void refresh() {
        refreshCountdown = REFRESH_INTERVAL_TICKS;
        entries.clear();

        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (minecraft == null || minecraft.world == null) return;

        for (Object loaded : minecraft.world.getEntities()) {
            Entity entity = (Entity) loaded;
            if (!entity.dead) entries.add(entity);
        }

        Entity player = minecraft.player;
        if (player != null) {
            entries.sort((left, right) ->
                    Double.compare(distanceBetween(player, left), distanceBetween(player, right)));
        }
    }

    private void renderScrollbar(int visible, int listHeight) {
        if (entries.size() <= visible) return;

        int trackX = contentX() + contentWidth() - 3;
        Draw.rect(trackX, contentY(), trackX + 3, contentY() + listHeight, Theme.PANEL_SUNKEN);

        int thumbHeight = Math.max(8, listHeight * visible / entries.size());
        int thumbTop = contentY() + (listHeight - thumbHeight) * scrollRow / (entries.size() - visible);
        Draw.rect(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, Theme.BORDER_FOCUSED);
    }

    private void clampScroll(int visible) {
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, entries.size() - visible)));
    }

    private static double distanceBetween(Entity from, Entity to) {
        double dx = from.x - to.x;
        double dy = from.y - to.y;
        double dz = from.z - to.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
