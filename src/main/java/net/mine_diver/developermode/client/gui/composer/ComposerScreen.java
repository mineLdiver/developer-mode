package net.mine_diver.developermode.client.gui.composer;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.DevScreen;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.radial.RadialScreen;
import net.mine_diver.developermode.feature.entity.FrozenEntities;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The desktop the developer tools live on.
 *
 * <p>There is exactly one, and it survives being closed, so reopening it puts
 * every window back where it was left. The radial menu is still reachable from
 * in here, which is how new windows arrive.
 */
public final class ComposerScreen extends DevScreen {
    private static final ComposerScreen INSTANCE = new ComposerScreen();

    private static final int CASCADE_STEP = 18;
    private static final int CASCADE_WRAP = 6;
    private static final int STATUS_BAR_HEIGHT = 12;

    /** Back to front. The last one is focused. */
    private final List<DevWindow> windows = new ArrayList<>();
    /** Added while the screen had no size yet, positioned on the next init. */
    private final List<DevWindow> unplaced = new ArrayList<>();

    private DevWindow dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private int cascade;

    private ComposerScreen() {}

    public static ComposerScreen instance() {
        return INSTANCE;
    }

    public static void open() {
        DeveloperModeClient.minecraft().setScreen(INSTANCE);
    }

    /**
     * Brings up the composer showing a window of the given type, reusing one
     * that is already open rather than piling up duplicates.
     */
    public static void reveal(Class<? extends DevWindow> type, Supplier<? extends DevWindow> factory) {
        DevWindow existing = INSTANCE.find(type);
        if (existing == null) {
            INSTANCE.add(factory.get());
        } else {
            INSTANCE.focus(existing);
        }
        open();
    }

    /** The open window of the given type, or null. */
    @SuppressWarnings("unchecked")
    public <W extends DevWindow> W find(Class<W> type) {
        for (DevWindow window : windows) {
            if (type.isInstance(window)) return (W) window;
        }
        return null;
    }

    public void add(DevWindow window) {
        windows.add(window);
        unplaced.add(window);
        if (width > 0) placeAll();
    }

    public void close(DevWindow window) {
        if (windows.remove(window)) window.onClosed();
        unplaced.remove(window);
        if (dragging == window) dragging = null;
    }

    /** Back to front, as they are stacked. */
    public List<DevWindow> windows() {
        return Collections.unmodifiableList(windows);
    }

    public DevWindow focused() {
        return windows.isEmpty() ? null : windows.get(windows.size() - 1);
    }

    public void focus(DevWindow window) {
        if (windows.remove(window)) windows.add(window);
    }

    @Override
    public void init() {
        placeAll();
        for (DevWindow window : windows) fitIntoView(window);
    }

    @Override
    public void tick() {
        for (DevWindow window : windows) window.tick();
    }

    @Override
    public void removed() {
        dragging = null;
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        placeAll();
        updateDrag(mouseX, mouseY);

        renderBackdrop();

        if (windows.isEmpty()) renderEmptyState();

        DevWindow focused = focused();
        DevWindow pointerOwner = pointerOwner(mouseX, mouseY);

        for (DevWindow window : windows) {
            boolean owns = window == pointerOwner;
            window.render(minecraft,
                    owns ? mouseX : DevWindow.POINTER_AWAY,
                    owns ? mouseY : DevWindow.POINTER_AWAY,
                    delta, window == focused);
        }
        for (DevWindow window : windows) {
            boolean owns = window == pointerOwner;
            window.renderOverlay(minecraft,
                    owns ? mouseX : DevWindow.POINTER_AWAY,
                    owns ? mouseY : DevWindow.POINTER_AWAY);
        }

        renderStatusBar();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        DevWindow window = windowAt(mouseX, mouseY);
        if (window == null) {
            // Clicking bare desktop drops text focus, the way clicking off a
            // field does anywhere else.
            for (DevWindow other : windows) other.clearTypingFocus();
            return;
        }

        focus(window);

        if (window.isOverClose(mouseX, mouseY)) {
            if (button == 0) close(window);
            return;
        }
        if (window.isOverTitleBar(mouseX, mouseY)) {
            if (button == 0) {
                dragging = window;
                dragOffsetX = mouseX - window.x;
                dragOffsetY = mouseY - window.y;
            }
            return;
        }

        window.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) dragging = null;

        DevWindow focused = focused();
        if (focused != null) focused.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void mouseScrolled(int mouseX, int mouseY, int direction) {
        DevWindow window = windowAt(mouseX, mouseY);
        if (window != null) window.mouseScrolled(mouseX, mouseY, direction);
    }

    /**
     * The window the pointer belongs to: the topmost one under it, or whatever
     * is being dragged, since a fast drag can leave the cursor trailing outside
     * the window it is carrying.
     */
    private DevWindow pointerOwner(int mouseX, int mouseY) {
        return dragging != null ? dragging : windowAt(mouseX, mouseY);
    }

    /**
     * Topmost window containing the point. The single rule that hover,
     * clicking and scrolling all go through, so they cannot disagree.
     */
    private DevWindow windowAt(int x, int y) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            DevWindow window = windows.get(i);
            if (window.contains(x, y)) return window;
        }
        return null;
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        DevWindow focused = focused();

        // The menu key always summons the radial, even while a text field has
        // focus. Losing the ability to type a backtick is a smaller cost than
        // the menu key sometimes doing nothing.
        if (keyCode == DeveloperModeClient.MENU_KEY.code) {
            RadialScreen.open(this);
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (focused != null && focused.clearTypingFocus()) return;
            minecraft.setScreen(null);
            return;
        }

        if (focused != null) focused.keyPressed(character, keyCode);
    }

    private void updateDrag(int mouseX, int mouseY) {
        if (dragging == null) return;

        // The release event can be missed if the button comes up while another
        // screen has focus, so trust the live button state too.
        if (!Mouse.isButtonDown(0)) {
            dragging = null;
            return;
        }

        dragging.x = clamp(mouseX - dragOffsetX, 8 - dragging.width, width - 8);
        dragging.y = clamp(mouseY - dragOffsetY, 0, height - DevWindow.TITLE_BAR_HEIGHT);
    }

    private void placeAll() {
        if (unplaced.isEmpty() || width <= 0) return;

        for (DevWindow window : unplaced) {
            int offset = (cascade++ % CASCADE_WRAP) * CASCADE_STEP;
            window.x = 16 + offset;
            window.y = 16 + offset;
            fitIntoView(window);
        }
        unplaced.clear();
    }

    private void fitIntoView(DevWindow window) {
        window.width = Math.min(window.width, Math.max(80, width - 8));
        window.height = Math.min(window.height, Math.max(DevWindow.TITLE_BAR_HEIGHT + 16, height - 8 - STATUS_BAR_HEIGHT));
        window.x = clamp(window.x, 0, Math.max(0, width - window.width));
        window.y = clamp(window.y, 0, Math.max(0, height - window.height - STATUS_BAR_HEIGHT));
    }

    private void renderEmptyState() {
        Draw.textCentered(minecraft, "Nothing open", width / 2, height / 2 - 10, Theme.TEXT_DIM);
        Draw.textCentered(minecraft, "hold " + menuKeyName() + " and flick a direction",
                width / 2, height / 2 + 2, Theme.TEXT_FAINT);
    }

    private void renderStatusBar() {
        int top = height - STATUS_BAR_HEIGHT;
        Draw.rect(0, top, width, height, Theme.PANEL);
        Draw.rect(0, top, width, top + 1, Theme.BORDER);

        Draw.text(minecraft, "Developer Mode", 5, top + 2, Theme.ACCENT);

        int frozen = FrozenEntities.frozenCount();
        String hint = menuKeyName() + " menu    esc close    " + windows.size() + " open"
                + (frozen > 0 ? "    " + frozen + " frozen" : "");
        Draw.text(minecraft, hint, width - Draw.textWidth(minecraft, hint) - 5, top + 2, Theme.TEXT_FAINT);
    }

    private static String menuKeyName() {
        return Keyboard.getKeyName(DeveloperModeClient.MENU_KEY.code);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
