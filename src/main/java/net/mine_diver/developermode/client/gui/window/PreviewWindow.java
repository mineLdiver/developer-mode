package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.gui.Button;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.IsometricView;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.player.Warp;
import net.mine_diver.developermode.feature.world.Preview;
import net.minecraft.client.Minecraft;
import net.modificationstation.stationapi.api.registry.BlockRegistry;
import net.modificationstation.stationapi.api.util.Identifier;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * What the generator makes of the chunks around you, drawn from above at an
 * angle, without any of it reaching the world.
 *
 * <p>Built from the world's seed at the spot you are standing on, so it shows
 * exactly what generating there would give, and built fresh each time, so a
 * hotswapped change to the generator shows up on the next build. It fills in
 * from the middle out as the chunks come.
 *
 * <p>Scrolling zooms, dragging moves the picture, and the cut-away hides
 * everything above a height, with the shift key held while scrolling to move
 * it, which is how to see inside. Clicking a block goes to that spot in the
 * world.
 */
public final class PreviewWindow extends DevWindow {
    private static final int GAP = 3;
    private static final int FOOTER_HEIGHT = 11;
    private static final int MIN_RADIUS = 0;
    private static final int MAX_RADIUS = 6;
    private static final int DEFAULT_RADIUS = 2;
    /** Moved past this before letting go, a press is a drag rather than a click. */
    private static final int DRAG_THRESHOLD = 3;
    /** Split between generating and drawing, each small beside a frame. */
    private static final long BUDGET_NANOS = 3_000_000;

    private final Button buildButton = new Button("gui.developermode.preview.build");
    private final Button smallerButton = new Button("gui.developermode.preview.smaller");
    private final Button largerButton = new Button("gui.developermode.preview.larger");
    private final Button coloringButton = new Button(IsometricView.Coloring.BLOCKS.key);
    private final Button turnLeftButton = new Button("gui.developermode.preview.turn_left");
    private final Button turnRightButton = new Button("gui.developermode.preview.turn_right");
    private final Button lowerButton = new Button("gui.developermode.preview.lower");
    private final Button raiseButton = new Button("gui.developermode.preview.raise");

    private final IsometricView view = new IsometricView();

    private int radius = DEFAULT_RADIUS;
    private IsometricView.Hit hovered;
    private boolean pressedInView;
    private boolean dragging;
    private int pressX;
    private int pressY;
    private int lastX;
    private int lastY;

    public PreviewWindow() {
        super("gui.developermode.preview.title", 320, 240);
    }

    public static void open() {
        ComposerScreen.reveal(PreviewWindow.class, PreviewWindow::new);
    }

    @Override
    public void onClosed() {
        view.release();
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        Preview preview = view.preview();
        if (preview != null) {
            if (!preview.done()) preview.step(BUDGET_NANOS);
            view.step(BUDGET_NANOS);
        }

        renderToolbar(minecraft, mouseX, mouseY);
        updateDrag(mouseX, mouseY);

        int viewY = viewY();
        Draw.rect(contentX(), viewY, contentX() + contentWidth(), viewY + viewHeight(), Theme.PANEL_SUNKEN);
        if (preview == null) {
            Draw.textCentered(minecraft, Lang.get("gui.developermode.preview.empty"),
                    contentX() + contentWidth() / 2, viewY + viewHeight() / 2 - 4, Theme.TEXT_FAINT);
        }
        view.render(minecraft, contentX(), viewY, contentWidth(), viewHeight());

        hovered = overView(mouseX, mouseY) && !dragging
                ? view.pick(mouseX, mouseY, contentX(), viewY, contentWidth(), viewHeight())
                : null;
        renderFooter(minecraft);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (buildButton.enabled && buildButton.contains(mouseX, mouseY)) {
            build();
        } else if (smallerButton.enabled && smallerButton.contains(mouseX, mouseY)) {
            radius = Math.max(MIN_RADIUS, radius - 1);
        } else if (largerButton.enabled && largerButton.contains(mouseX, mouseY)) {
            radius = Math.min(MAX_RADIUS, radius + 1);
        } else if (coloringButton.enabled && coloringButton.contains(mouseX, mouseY)) {
            IsometricView.Coloring[] all = IsometricView.Coloring.values();
            view.setColoring(all[(view.coloring().ordinal() + 1) % all.length]);
        } else if (turnLeftButton.enabled && turnLeftButton.contains(mouseX, mouseY)) {
            view.turn(-1);
        } else if (turnRightButton.enabled && turnRightButton.contains(mouseX, mouseY)) {
            view.turn(1);
        } else if (lowerButton.enabled && lowerButton.contains(mouseX, mouseY)) {
            view.setCut(view.cut() - cutStep());
        } else if (raiseButton.enabled && raiseButton.contains(mouseX, mouseY)) {
            view.setCut(view.cut() + cutStep());
        } else if (button == 0 && overView(mouseX, mouseY)) {
            pressedInView = true;
            dragging = false;
            pressX = lastX = mouseX;
            pressY = lastY = mouseY;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button != 0 || !pressedInView) return;
        pressedInView = false;
        if (dragging) {
            dragging = false;
            return;
        }
        IsometricView.Hit hit = view.pick(mouseX, mouseY, contentX(), viewY(), contentWidth(), viewHeight());
        if (hit != null) Warp.requestOnto(hit.x(), hit.y(), hit.z());
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        if (!overView(mouseX, mouseY)) return;
        if (shiftDown()) view.setCut(view.cut() + direction);
        else view.zoom(direction);
    }

    private void build() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (minecraft.world == null || minecraft.player == null) return;
        int chunkX = (int) Math.floor(minecraft.player.x) >> 4;
        int chunkZ = (int) Math.floor(minecraft.player.z) >> 4;
        view.show(Preview.around(minecraft.world, chunkX, chunkZ, radius));
    }

    /**
     * Follows the pointer while the button is held, which the window is not
     * told about between the press and the release.
     */
    private void updateDrag(int mouseX, int mouseY) {
        if (!pressedInView) return;
        if (!Mouse.isButtonDown(0)) {
            // Let go somewhere the release did not reach, such as over
            // another window. Treated as the end of a drag, never as a click.
            pressedInView = false;
            dragging = false;
            return;
        }
        if (mouseX == DevWindow.POINTER_AWAY) return;
        if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > DRAG_THRESHOLD) dragging = true;
        if (dragging) view.pan(mouseX - lastX, mouseY - lastY);
        lastX = mouseX;
        lastY = mouseY;
    }

    private void renderToolbar(Minecraft minecraft, int mouseX, int mouseY) {
        Preview preview = view.preview();
        int y = contentY();
        int x = contentX();

        buildButton.bounds(x, y, 40);
        buildButton.render(minecraft, mouseX, mouseY);
        x += 40 + GAP;

        smallerButton.bounds(x, y, 12);
        smallerButton.enabled = radius > MIN_RADIUS;
        smallerButton.render(minecraft, mouseX, mouseY);
        x += 12 + GAP;

        int across = radius * 2 + 1;
        String area = Lang.get("gui.developermode.preview.area", across, across);
        Draw.text(minecraft, area, x, y + 2, Theme.TEXT_DIM);
        x += Draw.textWidth(minecraft, area) + GAP;

        largerButton.bounds(x, y, 12);
        largerButton.enabled = radius < MAX_RADIUS;
        largerButton.render(minecraft, mouseX, mouseY);
        x += 12 + GAP + 2;

        String progress = preview == null ? ""
                : !preview.done() ? Lang.get("gui.developermode.preview.generating", preview.made(), preview.count())
                : !view.settled() ? Lang.get("gui.developermode.preview.drawing")
                : Lang.get("gui.developermode.preview.terrain");
        Draw.text(minecraft, progress, x, y + 2, Theme.TEXT_FAINT);

        int y2 = y + 12 + GAP;
        x = contentX();
        boolean shown = preview != null;

        turnLeftButton.bounds(x, y2, 14);
        turnLeftButton.enabled = shown;
        turnLeftButton.render(minecraft, mouseX, mouseY);
        x += 14 + 1;
        turnRightButton.bounds(x, y2, 14);
        turnRightButton.enabled = shown;
        turnRightButton.render(minecraft, mouseX, mouseY);
        x += 14 + GAP * 2;

        lowerButton.bounds(x, y2, 12);
        lowerButton.enabled = shown;
        lowerButton.render(minecraft, mouseX, mouseY);
        x += 12 + GAP;
        String cut = shown ? Lang.get("gui.developermode.preview.cut", view.cut()) : Lang.get("gui.developermode.preview.cut.none");
        Draw.text(minecraft, cut, x, y2 + 2, Theme.TEXT_DIM);
        x += Draw.textWidth(minecraft, cut) + GAP;
        raiseButton.bounds(x, y2, 12);
        raiseButton.enabled = shown;
        raiseButton.render(minecraft, mouseX, mouseY);

        int coloringWidth = 50;
        coloringButton.bounds(contentX() + contentWidth() - coloringWidth, y2, coloringWidth);
        coloringButton.key = view.coloring().key;
        coloringButton.enabled = shown;
        coloringButton.render(minecraft, mouseX, mouseY);
    }

    private void renderFooter(Minecraft minecraft) {
        int y = contentY() + contentHeight() - FOOTER_HEIGHT + 2;
        Preview preview = view.preview();
        String text;
        int color = Theme.TEXT_FAINT;
        if (preview != null && preview.failure() != null) {
            text = Lang.of(preview.failure());
            color = Theme.DANGER;
        } else if (hovered != null) {
            Identifier id = BlockRegistry.INSTANCE.getId(hovered.state().getBlock());
            String name = hovered.state().getBlock().getTranslatedName();
            if (name == null || name.isEmpty() || name.endsWith(".name")) name = String.valueOf(id);
            text = Lang.get("gui.developermode.preview.hovered", name, hovered.x(), hovered.y(), hovered.z());
            color = Theme.TEXT_DIM;
        } else {
            text = preview == null ? "" : Lang.get("gui.developermode.preview.hint");
        }
        Draw.text(minecraft, Draw.ellipsize(minecraft, text, contentWidth()), contentX(), y, color);
    }

    /** From the buttons the cut moves eight blocks at a time, or one with shift held. */
    private static int cutStep() {
        return shiftDown() ? 1 : 8;
    }

    private static boolean shiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private boolean overView(int mouseX, int mouseY) {
        return mouseX >= contentX() && mouseX < contentX() + contentWidth()
                && mouseY >= viewY() && mouseY < viewY() + viewHeight();
    }

    private int viewY() {
        return contentY() + (Button.HEIGHT + GAP) * 2;
    }

    private int viewHeight() {
        return contentHeight() - (Button.HEIGHT + GAP) * 2 - FOOTER_HEIGHT;
    }
}
